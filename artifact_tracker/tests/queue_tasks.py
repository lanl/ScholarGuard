# -*- coding: utf-8 -*-



message = {
    "@context": [
        "https://www.w3.org/ns/activitystreams#",
        "http://mementoweb.org/test-ns#",
        {
            "tracker": "http://tracker.mementoweb.org/ns#"
        }
    ],
    "event": {
        "actor": {
            "type": "Application",
            "id": "https://myresearch.institute/",
            "name": "My Research Institute's Orchestrator Process"
        },
        "object": {
            "type": "Profile",
            "describes": [
                {
                    "type": "Person",
                    "id": "https://orcid.org/0000-0002-0698-2864",
                    "tracker:portals": {
                        "type": "Collection",
                        "totalItems": 2,
                        "items": [
                            {
                                "type": "Service",
                                "tracker:portal": {
                                    "name": "slideshare",
                                    "tracker:apiKey": "0uOLDknQ",
                                    "tracker:apiSecret": "t0iSYkH8",
                                    "tracker:username": "soeren1611",
                                    "tracker:lastTracked": "2018-11-07T16:45:14Z"
                                }
                            },
                            {
                                "type": "Service",
                                "tracker:portal": {
                                    "name": "github",
                                    "tracker:apiKey": "87e6437b1260b13ebd3c",
                                    "tracker:apiSecret": "eb9f9560aaf02352e31e247985c71e66d73437dd",
                                    "tracker:username": "soeren1611",
                                    "tracker:lastToken": "\"13bdc906742bf79f42251514bb0bc9fc\"",
                                    "tracker:lastTracked": "2018-11-07T17:46:28Z"
                                }
                            }
                        ]
                    }
                }
            ]
        },
        "to": "https://myresearch.institute/orchestrator/tracker/inbox/",
        "tracker:eventBaseUrl": "https://myresearch.institute/tracker/event/",
        "published": "2018-11-27T20:48:39Z",
        "type": [
            "Offer"
        ]
    }
}


def queue_tasks(message):
    batch_apis = ["figshare", "blogger", "wordpress"]
    batch_queue = {}
    users = message.get("event", {}).get("object", {}).get("describes", [])
    ldn_inbox_url = message.get("event", {}).get("to")
    event_base_url = message.get("event", {}).get("tracker:eventBaseUrl")
    if not ldn_inbox_url or not event_base_url:
        return False

    for u in users:
        user_id = u.get("id")
        portals = u.get("tracker:portals", {}).get("items", [])
        for p in portals:
            portal_user = {}
            # remove namespace from keys
            for key, value in p.get("tracker:portal", {}).items():
                portal_user[
                    key.replace("tracker:", "")
                ] = value
            portal_user["id"] = user_id
            portal_name = portal_user.get("name")
            print([portal_user])

