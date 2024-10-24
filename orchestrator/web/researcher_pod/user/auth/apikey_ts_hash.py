# -*- coding: utf-8 -*-

from researcher_pod import pod
from datetime import datetime
import calendar
import hashlib

app = pod.app
LOG = pod.log


def get_ts_hash(api_secret=None):
    if not api_secret:
        LOG.debug("api_secret is needed.")
        return None, None

    now = datetime.utcnow()
    ts = calendar.timegm(now.utctimetuple())

    hsh = hashlib.sha1()
    text = f"{api_secret}{ts}"
    hsh.update(text.encode("utf8"))
    return ts, hsh.hexdigest()

