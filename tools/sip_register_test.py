#!/usr/bin/env python3
"""
SIP REGISTER smoke test (no dependencies). Checks that a SIP account's server, transport and credentials work,
independent of the Android app. Registers with a throw-away Contact, then immediately unregisters that Contact
(Expires: 0) so the real phone's registration is not disturbed.

Usage:
  SIP_PASSWORD=... python3 tools/sip_register_test.py --user 13811 --domain sip.sipup.org \
      --proxy 162.19.138.146 --transport tls [--port 5061] [--auth-user 13811]

Exit code 0 when REGISTER returns 200 OK.
"""
import argparse, hashlib, os, random, socket, ssl, string, sys, time


def rnd(n=10):
    return "".join(random.choice(string.ascii_lowercase + string.digits) for _ in range(n))


def md5(s):
    return hashlib.md5(s.encode()).hexdigest()


def parse_auth(header):
    # Digest realm="x", nonce="y", qop="auth", algorithm=MD5
    out = {}
    body = header.split(" ", 1)[1] if " " in header else header
    for part in body.split(","):
        if "=" in part:
            k, v = part.strip().split("=", 1)
            out[k.strip().lower()] = v.strip().strip('"')
    return out


class Conn:
    def __init__(self, host, port, transport, timeout=8):
        self.transport = transport
        self.addr = (host, port)
        if transport == "udp":
            self.s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            self.s.settimeout(timeout)
            self.s.connect(self.addr)
        else:
            raw = socket.create_connection(self.addr, timeout=timeout)
            if transport == "tls":
                ctx = ssl.create_default_context()
                try:
                    self.s = ctx.wrap_socket(raw, server_hostname=host)
                    print(f"TLS: certificate VERIFIED for {host}")
                except ssl.SSLCertVerificationError as e:
                    print(f"TLS: certificate NOT trusted ({e.verify_message}); continuing without verification")
                    raw = socket.create_connection(self.addr, timeout=timeout)
                    ctx = ssl.create_default_context()
                    ctx.check_hostname = False
                    ctx.verify_mode = ssl.CERT_NONE
                    self.s = ctx.wrap_socket(raw, server_hostname=host)
                cert = self.s.getpeercert(binary_form=False) or {}
                print(f"TLS: {self.s.version()} cipher={self.s.cipher()[0]} subject={cert.get('subject')}")
            else:
                self.s = raw
            self.s.settimeout(timeout)
        self.buf = b""
        self.local = self.s.getsockname()

    def send(self, msg):
        data = msg.encode()
        self.s.send(data)

    def recv(self):
        if self.transport == "udp":
            return self.s.recv(65535).decode(errors="replace")
        while b"\r\n\r\n" not in self.buf:
            chunk = self.s.recv(65535)
            if not chunk:
                raise ConnectionError("connection closed by server")
            self.buf += chunk
        head, rest = self.buf.split(b"\r\n\r\n", 1)
        length = 0
        for line in head.decode(errors="replace").split("\r\n"):
            if line.lower().startswith(("content-length:", "l:")):
                length = int(line.split(":", 1)[1].strip() or 0)
        while len(rest) < length:
            rest += self.s.recv(65535)
        self.buf = rest[length:]
        return (head + b"\r\n\r\n" + rest[:length]).decode(errors="replace")


def headers(resp):
    lines = resp.split("\r\n")
    hs = {}
    for l in lines[1:]:
        if ":" in l:
            k, v = l.split(":", 1)
            hs.setdefault(k.strip().lower(), v.strip())
    return lines[0], hs


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--user", required=True)
    ap.add_argument("--auth-user")
    ap.add_argument("--domain", required=True)
    ap.add_argument("--proxy", help="host to connect to (default: domain)")
    ap.add_argument("--transport", default="udp", choices=["udp", "tcp", "tls"])
    ap.add_argument("--port", type=int)
    ap.add_argument("--expires", type=int, default=60)
    a = ap.parse_args()
    password = os.environ.get("SIP_PASSWORD", "")
    host = a.proxy or a.domain
    port = a.port or (5061 if a.transport == "tls" else 5060)
    auth_user = a.auth_user or a.user
    print(f"Connecting {a.transport.upper()} {host}:{port} as {a.user}@{a.domain}")
    c = Conn(host, port, a.transport)
    lip, lport = c.local[0], c.local[1]
    call_id, tag, branch_base = rnd(16), rnd(8), "z9hG4bK" + rnd(8)
    contact_user = "smoketest-" + rnd(6)
    uri = f"sip:{a.domain}"
    tp = a.transport.upper()

    def register(cseq, expires, auth=None):
        msg = (
            f"REGISTER {uri} SIP/2.0\r\n"
            f"Via: SIP/2.0/{tp} {lip}:{lport};branch={branch_base}{cseq};rport\r\n"
            f"Max-Forwards: 70\r\n"
            f"From: <sip:{a.user}@{a.domain}>;tag={tag}\r\n"
            f"To: <sip:{a.user}@{a.domain}>\r\n"
            f"Call-ID: {call_id}\r\n"
            f"CSeq: {cseq} REGISTER\r\n"
            f"Contact: <sip:{contact_user}@{lip}:{lport};transport={a.transport}>\r\n"
            f"Expires: {expires}\r\n"
            f"User-Agent: MicroSIP-Android-smoketest\r\n"
        )
        if auth:
            msg += auth + "\r\n"
        msg += "Content-Length: 0\r\n\r\n"
        c.send(msg)
        while True:
            resp = c.recv()
            status, hs = headers(resp)
            print("<-", status, "| Server:", hs.get("server", hs.get("user-agent", "?")))
            if not status.startswith("SIP/2.0 1"):
                return status, hs

    def digest(hs, cseq_method="REGISTER"):
        hdr = hs.get("www-authenticate") or hs.get("proxy-authenticate")
        name = "Authorization" if "www-authenticate" in hs else "Proxy-Authorization"
        p = parse_auth(hdr)
        realm, nonce, qop = p.get("realm", ""), p.get("nonce", ""), p.get("qop", "")
        print(f"   auth challenge: realm={realm} algorithm={p.get('algorithm','MD5')} qop={qop}")
        ha1 = md5(f"{auth_user}:{realm}:{password}")
        ha2 = md5(f"{cseq_method}:{uri}")
        if "auth" in qop.split(","):
            nc, cnonce = "00000001", rnd(12)
            resp = md5(f"{ha1}:{nonce}:{nc}:{cnonce}:auth:{ha2}")
            extra = f', qop=auth, nc={nc}, cnonce="{cnonce}"'
        else:
            resp = md5(f"{ha1}:{nonce}:{ha2}")
            extra = ""
        opaque = f', opaque="{p["opaque"]}"' if "opaque" in p else ""
        return (f'{name}: Digest username="{auth_user}", realm="{realm}", nonce="{nonce}", uri="{uri}", '
                f'response="{resp}", algorithm=MD5{extra}{opaque}')

    status, hs = register(1, a.expires)
    if status.startswith("SIP/2.0 401") or status.startswith("SIP/2.0 407"):
        status, hs = register(2, a.expires, digest(hs))
    ok = status.startswith("SIP/2.0 200")
    if ok:
        print("RESULT: REGISTERED OK. Contact(s) now bound:", hs.get("contact", "?")[:300])
        # Remove only our throw-away binding.
        st2, hs2 = register(3, 0)
        if st2.startswith("SIP/2.0 401") or st2.startswith("SIP/2.0 407"):
            st2, _ = register(4, 0, digest(hs2))
        print("Unregister smoketest contact:", st2)
    else:
        print("RESULT: FAILED:", status)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
