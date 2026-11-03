# SPDX-License-Identifier: GPL-3.0-only
"""Create a local release key once. Never commit .signing or print passwords."""
import os, secrets, shutil, subprocess
from pathlib import Path
root = Path(__file__).resolve().parents[1]
folder = root / ".signing"
folder.mkdir(exist_ok=True)
key = folder / "shotegy-release.p12"
properties = folder / "signing.properties"
if key.exists() or properties.exists():
    raise SystemExit("Signing material already exists; nothing changed.")
keytool = shutil.which("keytool") or str(Path(os.environ["JAVA_HOME"]) / "bin/keytool.exe")
password = secrets.token_urlsafe(36)
environment = {**os.environ, "SHOTEGY_KEYSTORE_PASSWORD": password}
subprocess.run([keytool, "-genkeypair", "-keystore", str(key), "-storetype", "PKCS12",
    "-storepass:env", "SHOTEGY_KEYSTORE_PASSWORD", "-keypass:env", "SHOTEGY_KEYSTORE_PASSWORD",
    "-alias", "shotegy", "-keyalg", "RSA", "-keysize", "4096", "-sigalg", "SHA256withRSA",
    "-validity", "10000", "-dname", "CN=Hiroshi33XX, OU=Shotegy", "-noprompt"],
    env=environment, check=True, capture_output=True)
properties.write_text("storeFile=.signing/shotegy-release.p12\nkeyAlias=shotegy\nstorePassword=" + password + "\nkeyPassword=" + password + "\n", encoding="utf-8")
if os.name == "nt":
    identity = subprocess.check_output(["whoami"], text=True).strip()
    subprocess.run(["icacls", str(folder), "/inheritance:r", "/grant:r", identity + ":(OI)(CI)F", "SYSTEM:(OI)(CI)F"], check=True, capture_output=True)
else:
    folder.chmod(0o700); key.chmod(0o600); properties.chmod(0o600)
print("Release key created in .signing. Back up this PRIVATE directory offline.")
