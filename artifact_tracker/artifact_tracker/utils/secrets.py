import os
import base64
from datetime import datetime
import calendar
import hashlib


def make_secrets():
    """
    Create secrets for database
    """
    secret_file_name = os.path.join(
        os.path.dirname(os.path.realpath(__file__)),
        "../../secrets", "secrets"
    )

    lines = []
    if os.path.isfile(secret_file_name):
        with open(secret_file_name, "r", encoding="utf8") as sf:
            lines = sf.readlines()

    if not len(lines) == 2:
        secret_key = base64.urlsafe_b64encode(os.urandom(32))
        salt = base64.urlsafe_b64encode(os.urandom(16))
        with open(secret_file_name, "w+", encoding="utf8") as sf:
            sf.write("%s\n%s" % (secret_key.decode("utf8"),
                                 salt.decode("utf8")))


def get_ts_hash(api_secret):
    now = datetime.utcnow()
    ts = calendar.timegm(now.utctimetuple())

    hsh = hashlib.sha1()
    text = f"{api_secret}{ts}"
    hsh.update(text.encode("utf8"))
    return ts, hsh.hexdigest()
