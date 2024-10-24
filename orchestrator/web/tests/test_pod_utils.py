# -*- coding: utf-8 -*-

import os
import json
from tests import ResearcherPodTests  # unittest.TestCase
from researcher_pod.pod.utils import is_complete_portal_config,\
    post_to_ldn_inbox, get_login_portals
from researcher_pod.store.portal_config import PortalConfig


class TestUserUtils(ResearcherPodTests):
    payload_file = os.path.join(os.path.dirname(__file__),
                                "data/AS2_sample.json")
    with open(payload_file) as f:
        as2_payload = json.load(f)

    def test_get_login_portals(self):
        assert not get_login_portals()

        pc = PortalConfig.query.filter_by(portal_name="github").first()
        pc.client_id = b"ssss"
        pc.client_secret = b"ffff"

        self.pod.db.session.add(pc)
        self.pod.db.session.commit()
        lp = get_login_portals()
        # only orcid login is allowed. so github should not show up.
        assert len(lp) == 0

        pc = PortalConfig.query.filter_by(portal_name="orcid").first()
        pc.client_id = b"ssss"
        pc.client_secret = b"ffff"

        self.pod.db.session.add(pc)
        self.pod.db.session.commit()
        lp = get_login_portals()
        assert len(lp) == 1
        assert "orcid" in lp[0][1]
        assert "orcid" == lp[0][0].portal_name

    def test_is_complete_portal_config(self):

        pc = PortalConfig.query.filter_by(portal_name="github").first()
        assert not is_complete_portal_config(pc)

        pc.client_id = b"ssss"
        pc.client_secret = b"ffff"

        assert is_complete_portal_config(pc)

    def test_post_to_ldn_inbox(self):
        events = []
        assert not post_to_ldn_inbox(events)

        events.append(self.as2_payload)
        assert post_to_ldn_inbox(events)
