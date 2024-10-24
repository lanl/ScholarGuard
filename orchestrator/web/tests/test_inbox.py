# -*- coding: utf-8 -*-
from tests import ResearcherPodTests
from requests.utils import parse_header_links
from werkzeug.utils import cached_property
import json


class TestInbox(ResearcherPodTests):

    as2_payload = {
      #"@context": "https://www.w3.org/ns/activitystreams",
      "published": "2016-12-10T15:20:33Z",

      "type": "Mention",
      "href": "http://twitter.com/janedoe",
      "name": "Jane Doe",

      "actor": {
        "type": "Person",
        "id": "http://john.smith/webid",
        "name": "John Smith",
        "url": "http://twitter.com/johnsmith",
        "image": {
          "type": "Link",
          "href": "http://twitter_profile/image.jpg",
          "mediaType": "image/jpeg"
        }
      },

      "object": {
        "id": "http://twitter.com/status/857384837372727272884",
        "type": "Note",
        "url": "http://twitter.com/status/857384837372727272884",
        "content": "@janedoe, sup"
      }
    }

    @cached_property
    def inbox_url(self):
        resp = self.client.get("/")
        assert resp.status_code == 200
        lh = resp.headers.get("Link")
        assert lh
        parsed_lh = parse_header_links(lh)
        inbox_url = None
        for link in parsed_lh:
            if link.get("rel") == "https://www.w3.org/ns/ldp#inbox":
                inbox_url = link.get("url")
        assert inbox_url
        return inbox_url

    def test_head_inbox(self):
        resp = self.client.head(self.inbox_url)
        assert resp.status_code == 200
        assert "get" in resp.headers.get("Allow").lower()
        assert "head" in resp.headers.get("Allow").lower()
        assert "options" in resp.headers.get("Allow").lower()
        assert "post" in resp.headers.get("Allow").lower()
        assert resp.headers.get("Link")
        assert "application/ld+json" in resp.headers.get("Accept-Post")

    def test_get_inbox(self):
        resp = self.client.get(self.inbox_url)
        assert resp.status_code == 200
        assert "get" in resp.headers.get("Allow").lower()
        assert "head" in resp.headers.get("Allow").lower()
        assert "options" in resp.headers.get("Allow").lower()
        assert "post" in resp.headers.get("Allow").lower()
        assert resp.headers.get("Link")
        assert "application/ld+json" in resp.headers.get("Accept-Post")
        assert "application/ld+json" in resp.content_type
        data = json.loads(resp.data)
        assert len(data[0].get("@type")) > 0

        hdrs = [("Accept", "text/turtle")]
        resp = self.client.get(self.inbox_url, headers=hdrs)
        assert resp.status_code == 200
        assert "text/turtle" in resp.content_type
        assert resp.content_length > 0

    def test_post_inbox(self):
        resp = self.client.post(self.inbox_url)
        assert resp.status_code == 500
        assert "content type" in resp.data.decode("utf8").lower()

        hdrs = [("Content-Type", "application/ld+json")]
        resp = self.client.post(self.inbox_url, headers=hdrs)
        assert resp.status_code == 500
        assert "empty payload" in resp.data.decode("utf8").lower()

        hdrs = [("Content-Type", "application/ld+json")]
        data = "bad data"
        resp = self.client.post(self.inbox_url, headers=hdrs,
                                data=data)
        assert resp.status_code == 500
        assert "cannot process payload" in resp.data.decode("utf8").lower()

        hdrs = [("Content-Type", "application/ld+json")]
        resp = self.client.post(self.inbox_url, headers=hdrs,
                                data=json.dumps(self.as2_payload))
        assert resp.status_code == 201

    def test_tracker_name(self):
        from researcher_pod.ldn.inbox import get_tracker_name

        tn = get_tracker_name("http://www.google.com")
        assert tn == "google"

        tn = get_tracker_name("http://www.google.co.uk")
        assert tn == "google.co.uk"

        tn = get_tracker_name("google")
        assert tn == "google"

        assert not get_tracker_name("")

    def test_compute_payload_hash(self):
        from researcher_pod.ldn.inbox import compute_payload_hash
        assert compute_payload_hash(b"AAA") is not None
        assert compute_payload_hash("AAA") is not None
        assert not compute_payload_hash({})
        assert not compute_payload_hash(None)

    def test_is_valid_as2_payload(self):
        from researcher_pod.ldn.inbox import is_valid_as2_payload
        assert not is_valid_as2_payload("aaa", "application/ld+json")

        # payload shd be bytes not dict
        assert not is_valid_as2_payload(self.as2_payload, "application/ld+json")

        assert is_valid_as2_payload(
            json.dumps(self.as2_payload),
            "application/ld+json"
        )

        ttl = """
        @prefix dcterms: <http://purl.org/dc/terms/>.
        @prefix ldp: <http://www.w3.org/ns/ldp#>.

        <http://example.org/c1/>
           a ldp:BasicContainer;
           dcterms:title "A very simple container";
           ldp:contains <r1>, <r2>, <r3>."""
        assert is_valid_as2_payload(
            ttl, "text/turtle"
        )

    def test_is_duplicate_activity(self):
        from researcher_pod.ldn.inbox import is_dulplicate_activity,\
            save_activity_to_db, compute_payload_hash
        hash = compute_payload_hash(json.dumps(self.as2_payload))
        assert hash is not None
        # no activities in db
        assert not is_dulplicate_activity(hash)

        self._create_user()
        saved = save_activity_to_db(self.as2_payload,
                                    "test",
                                    hash)
        assert saved
        assert is_dulplicate_activity(hash)

