# -*- coding: utf-8 -*-
import requests
from flask import url_for
from sqlalchemy import not_
from researcher_pod.store.portal_config import PortalConfig
from researcher_pod.store.user import User, Portal
from researcher_pod.store.tracker import TrackerState
import celery
from datetime import datetime, timedelta
from researcher_pod.store.es.index import INDEX_NAME


def get_login_portals() -> [PortalConfig]:
    """
    Returns a list of portals that allow authentication in to the pod
    based on the configuration specified in the `config.yaml` file.

    This function looks for the property `allow_login` to be set to
    `true` in the portal configurations.
    :return: (List(PortalConfig)) List of
    :func:`researcher_pod.store.portal_config.PortalConfig`
    for the portals that allow login.
    """
    from researcher_pod import pod
    pcs = []
    portal_configs = PortalConfig.query.all()
    for pc in portal_configs:
        if bool(pod.app.config.get(
                "%s_ALLOW_LOGIN" % pc.portal_name.upper())) \
                and is_complete_portal_config(pc):
            # addresses issue 21
            if pc.portal_name == "orcid":
                login_url = url_for(
                    "%s_portal.get_request_token" % pc.auth_type.name,
                    portal_name=pc.portal_name, _external=True)
                pcs.append((pc, login_url))
    return pcs


def is_complete_portal_config(portal_config) -> bool:
    """
    Checks if the saved config information in the db for a portal
    is complete so that authentication can be supported for that portal.
    :param portal_config: (PortalConfig) the portal configuration retrieved
    from the db
    :return: (bool) True if the portal is complete, else False.
    """
    if bool(portal_config.portal_name) and \
            bool(portal_config.auth_type) and \
            bool(portal_config.client_id) and \
            bool(portal_config.client_secret) and \
            bool(portal_config.base_authorization_url) and \
            bool(portal_config.access_token_url):
        return True
    return False


def make_user_profile(user_id: str):
    return {
        "type": "Person",
        "id": user_id,
        "tracker:portals": {
            "type": "Collection",
            "totalItems": 0,
            "items": []
        }
    }


def batch_user_portals(portal_name: str,
                       check_should_be_sent: bool=True):
    """
    Helper to generate user profiles for all users
    that are in a batch portal.
    Sends multiple users to the tracker component.
    """
    from researcher_pod import pod, app
    available_portals = list(map((lambda x: x.split(".")[-1]),
                                 app.config.get("CELERY_TASKS_IMPORT")))
    describes = []
    users_cache = {}
    with pod.app.app_context():
        portals = pod.db.session.query(
            Portal, User, PortalConfig, TrackerState)\
            .select_from(Portal)\
            .outerjoin(TrackerState)\
            .filter(User.id == Portal.user_id)\
            .filter(PortalConfig.portal_name == portal_name)\
            .filter(PortalConfig.portal_name.in_(available_portals))\
            .filter(Portal.portal_config_id == PortalConfig.id).all()

        if not portals:
            return []
        for p in portals:
            portal, user, portal_config, state = p
            if check_should_be_sent:
                if not should_be_sent(portal.last_sent,
                                      portal_config.portal_name):
                    continue
            users_cache.setdefault(user.id,
                                   make_user_profile(user.id))
            portal.last_sent = datetime.now()
            template = portal_as2_template(
                portal,
                user,
                portal_config,
                state
            )
            users_cache[user.id]["tracker:portals"]["items"].append(
                template
            )
            users_cache[user.id]["tracker:portals"]["totalItems"] += 1
        pod.db.session.commit()
    describes = [users_cache[u] for u in users_cache]

    return describes


def should_be_sent(last_sent_datetime, portal_name):
    """
    Compare current datetime to last sent and interval at which
    a portal should be sent. If a portal was already sent within
    the scheduled time do not send again.

    Used to prevent sending duplicate messages to tracker component
    in short periods of time.
    """
    # TODO: changed 1 day to portal config or db value interval
    if not last_sent_datetime:
        return True
    expected_time = datetime.now() - timedelta(days=1)
    if last_sent_datetime < expected_time:
        return True
    return False


def make_as2_profile(
        user_id: str,
        check_should_be_sent: bool=True,
        portal_name: str=None):
    """
    Helper to generate user profiles for non-batch
    api portals.
    """
    from researcher_pod import pod, app

    user_profile = make_user_profile(user_id)
    # starting to this should be a db variable
    batch_apis = ["figshare"]
    available_portals = list(map((lambda x: x.split(".")[-1]),
                                 app.config.get("CELERY_TASKS_IMPORT")))
    with pod.app.app_context():
        portals = pod.db.session.query(
            Portal, User, PortalConfig, TrackerState)\
            .select_from(Portal)\
            .outerjoin(TrackerState)\
            .filter(Portal.user_id == user_id)\
            .filter(User.id == user_id)\
            .filter(PortalConfig.portal_name.in_(available_portals))\
            .filter(not_(PortalConfig.portal_name.in_(batch_apis)))\
            .filter(Portal.portal_config_id == PortalConfig.id)\
            .group_by(Portal.id)\
            .order_by(TrackerState.last_updated)

        if portal_name:
            portals = portals.filter(PortalConfig.portal_name == portal_name)

        portals = portals.all()
        user_profile["tracker:portals"]["totalItems"] = 0
        if not portals:
            return user_profile
        for p in portals:
            portal, user, portal_config, state = p
            if check_should_be_sent:
                if not should_be_sent(portal.last_sent,
                                      portal_config.portal_name):
                    continue
            portal.last_sent = datetime.now()
            template = portal_as2_template(
                portal,
                user,
                portal_config,
                state
            )
            user_profile["tracker:portals"]["items"].append(
                template
            )
            user_profile["tracker:portals"]["totalItems"] += 1
        pod.db.session.commit()

        return user_profile


def portal_as2_template(
        portal: Portal,
        user: User,
        portal_config: PortalConfig,
        state: TrackerState
        ):
    from researcher_pod import pod
    portal_as2 = {
        "type": "Service",
        "tracker:portal": {}
    }
    tportal = {
        "name": portal_config.portal_name
    }
    last_tracked = user.confirmed_at or datetime.strptime(
        pod.app.config.get("DISALLOW_EVENTS_BEFORE"), "%Y-%m-%dT%H:%M:%SZ")
    last_tracked = last_tracked.strftime("%Y-%m-%dT%H:%M:%SZ")
    last_token = None
    if state:
        last_tracked = state.last_updated or \
            user.confirmed_at or \
            datetime.strptime(
                pod.app.config.get("DISALLOW_EVENTS_BEFORE"),
                "%Y-%m-%dT%H:%M:%SZ")
        last_tracked = last_tracked.strftime("%Y-%m-%dT%H:%M:%SZ")
        last_token = state.tracker_state
    tportal.setdefault("tracker:apiKey", portal_config.client_id)
    tportal.setdefault("tracker:apiSecret", portal_config.client_secret)
    tportal.setdefault("tracker:username", portal.portal_username)
    tportal.setdefault("tracker:userId", portal.portal_user_id)
    tportal.setdefault("tracker:portalUrl", portal.portal_url)
    tportal.setdefault("tracker:oauthToken", portal_config.oauth_token)
    tportal.setdefault("tracker:oauthSecret", portal_config.oauth_secret)
    tportal.setdefault("tracker:lastToken", last_token)
    for key, val in tportal.items():
        if isinstance(val, bytes):
            tportal[key] = val.decode('utf8')
    # check values of dictionary, if value is empty remove key
    tportal = {k: v for k, v in tportal.items() if v}

    tportal["tracker:lastTracked"] = last_tracked

    portal_as2["tracker:portal"] = tportal
    return portal_as2


def tracker_as2_template(
        to_inbox_url: str,
        event_base_url: str,
        actor_id: str,
        organization_full_name: str=None):
    """
    Outgoing AS2 message from orchestrator to the
    tracker component.
    """
    as2_payload = {"@context": {}, "event": {}}
    as2_payload["@context"] = [
        "https://www.w3.org/ns/activitystreams#",
        "http://mementoweb.org/test-ns#",
        {
            "tracker": "http://tracker.mementoweb.org/ns#"
        }
    ]
    event = {"actor": {}, "object": {
        "type": "Profile",
        "describes": []
    }}
    event["to"] = to_inbox_url
    event["tracker:eventBaseUrl"] = event_base_url
    event["published"] = datetime.now().strftime("%Y-%m-%dT%H:%M:%SZ")
    event["type"] = ["Offer"]
    event["actor"] = {
      "type": "Application",
      "id": actor_id
    }
    if organization_full_name:
        event["actor"]["name"] = "{}'s Orchestrator Process".\
            format(organization_full_name)
    as2_payload["event"] = event

    return as2_payload


def dereference_as2(
        to_inbox_url: str,
        event_base_url: str,
        actor_id: str,
        object_id: str,
        organization_full_name: str=None):
    """
    Outgoing AS2 message from orchestrator to the
    Capture and Archiver components. These components
    use the object id to dereference the AS2 event message
    stored somewhere indicated by the object id.
    """
    as2_payload = {"@context": {}, "event": {}}
    as2_payload["@context"] = [
        "https://www.w3.org/ns/activitystreams#",
        "http://mementoweb.org/test-ns#",
        {
            "tracker": "http://tracker.mementoweb.org/ns#"
        }]
    event = {}
    event["to"] = to_inbox_url
    event["tracker:eventBaseUrl"] = event_base_url
    event["published"] = datetime.now().strftime("%Y-%m-%dT%H:%M:%SZ")
    event["type"] = ["Offer"]
    event["actor"] = {
      "type": "Application",
      "id": actor_id
    }
    if organization_full_name:
        event["actor"]["name"] = "{}'s Orchestrator Process".\
            format(organization_full_name)
    event["object"] = {
      "type": ["Link", "Activity"],
      "id": object_id if object_id.endswith("/") else object_id + "/"
    }
    as2_payload["event"] = event
    return as2_payload


def post_to_ldn_inbox(
        message: dict=None,
        inbox_url: str=None) -> bool:
    """
    Posts a list of users & portals to the component ldn inboxes.

    :param events: (List(dict)) The list of activities as a dict.
    :return: (bool) True if all the events were successfully accepted by
    the inbox. False otherwise.
    """
    from researcher_pod import pod

    if not inbox_url:
        return False

    if not isinstance(message, dict):
        pod.log.error("The ActivityStream " +
                      "payload is not of type dict.")
        return False

    pod.log.debug("POSTing data to LDN Inbox at: {}".format(inbox_url))
    try:
        resp = requests.post(inbox_url,
                             json=message,
                             headers={"Content-Type":
                                      "application/ld+json"})
    except (requests.exceptions.ConnectionError,
            requests.exceptions.ConnectTimeout) as e:
        pod.log.error(f"Error connecting to LDN inbox: {inbox_url}"
                      f"\nError: {e}")
        return False

    pod.log.debug(resp.status_code)
    if not resp.status_code >= 200\
            and resp.status_code < 300:
        # TODO: handle error
        pod.log.error("OUTBOX returned non-200 status: {}"
                      .format(resp.status_code))
        pod.log.debug(resp.headers)
        pod.log.debug(resp.text)
        return False

    return True


@celery.task
def run_user_portals(user_id: str, portal_name: str=None):
    from researcher_pod import app
    config = app.config
    tracker_inbox = config.get(
        "COMPONENT_INBOX_ENDPOINT").format("tracker")
    to_inbox = config.get(
        "ORCHESTRATOR_INBOX_ENDPOINT").format("tracker")
    event_base_url = config.get(
        "ORCHESTRATOR_EVENT_BASE_URL").format("tracker")
    actor_id = "{}://{}/".format(
        config.get("PREFERRED_URL_SCHEME"),
        config.get("SERVER_NAME"))
    org_name = config.get("ORGANIZATION_FULL_NAME")
    as2_msg = tracker_as2_template(
        to_inbox_url=to_inbox,
        event_base_url=event_base_url,
        actor_id=actor_id,
        organization_full_name=org_name
    )
    portal_desc = make_as2_profile(user_id, portal_name=portal_name)
    if portal_desc["tracker:portals"]["totalItems"] > 0:
        as2_msg["event"]["object"]["describes"] = [portal_desc]
    else:
        return
    #as2_msg["event"]["object"]["describes"] = [
    #    make_as2_profile(user_id, portal_name=portal_name)]
    import json
    print(json.dumps(as2_msg, indent=2))
    return post_to_ldn_inbox(
        message=as2_msg,
        inbox_url=tracker_inbox
    )


@celery.task
def run_batch_portal(portal_name: str):
    from researcher_pod import app
    config = app.config
    tracker_inbox = config.get(
        "COMPONENT_INBOX_ENDPOINT").format("tracker")
    to_inbox = config.get(
        "ORCHESTRATOR_INBOX_ENDPOINT").format("tracker")
    event_base_url = config.get(
        "ORCHESTRATOR_EVENT_BASE_URL").format("tracker")
    actor_id = "{}://{}/".format(
        config.get("PREFERRED_URL_SCHEME"),
        config.get("SERVER_NAME"))
    org_name = config.get("ORGANIZATION_FULL_NAME")
    as2_msg = tracker_as2_template(
        to_inbox_url=to_inbox,
        event_base_url=event_base_url,
        actor_id=actor_id,
        organization_full_name=org_name
    )
    portal_desc = batch_user_portals(portal_name)  # type: dict
    if len(portal_desc) > 0:
        as2_msg["event"]["object"]["describes"] = portal_desc
    else:
        return
    # as2_msg["event"]["object"]["describes"] = batch_user_portals(portal_name)
    import json
    print(json.dumps(as2_msg, indent=2))
    return post_to_ldn_inbox(
        message=as2_msg,
        inbox_url=tracker_inbox
    )


def resend_missed_events_to_archiver():
    """
    This function finds captured events that have been missed by the
    archiver process, and re-sends these messages to the archiver inbox.
    :return:
    """
    from researcher_pod import app, pod
    from researcher_pod.ldn.inbox import send_to_component
    import time

    tracker_query = {
        "query": {
            "bool": {
                "must": [{
                    "term": {
                        "activity.event.type": {
                            "value": "tracker:Capture"
                        }
                    }
                }
                ]
            }
        }
    }

    tracker_resp = pod.es.search(
        index=INDEX_NAME,
        body=tracker_query,
        size=20000
    )

    for event in tracker_resp["hits"]["hits"]:
        cap_event_id = event.get("_id")
        tracker_event_id = event.get("_source").get("tracker_parent_id")
        archiver_query = {
            "query": {
                "bool": {
                    "must": [{
                        "term": {
                            "activity.event.type": {
                                "value": "tracker:Archiver"
                            }
                        }
                    },
                        {
                            "term": {
                                "tracker_parent_id": {
                                    "value": tracker_event_id
                                }
                            }
                        }
                    ]
                }
            }
        }

        archiver_resp = pod.es.search(index=INDEX_NAME, body=archiver_query)
        if archiver_resp["hits"]["total"] != 1:
            print(cap_event_id)
            send_to_component(component_name="archiver",
                              object_id="https://myresearch.institute/capture/event/" + cap_event_id)
            time.sleep(1)


def resend_missed_events_to_capture():
    """
    This function finds tracked events that have been missed by the
    capture process, and re-sends these messages to the capture inbox.
    :return:
    """
    from researcher_pod import app, pod
    from researcher_pod.ldn.inbox import send_to_component
    tracker_query = {
        "query": {
            "bool": {
                "must": [{
                    "term": {
                        "activity.event.type": {
                            "value": "tracker:Tracker"
                        }
                    }
                }
                ]
            }
        }
    }

    tracker_resp = pod.es.search(
        index=INDEX_NAME,
        body=tracker_query,
        size=20000
    )

    for event in tracker_resp["hits"]["hits"]:
        event_id = event.get("_id")
        capture_query = {
            "query": {
                "bool": {
                    "must": [{
                        "term": {
                            "activity.event.type": {
                                "value": "tracker:Capture"
                            }
                        }
                    },
                    {
                        "term": {
                            "tracker_parent_id": {
                                "value": event_id
                            }
                        }
                    }
                    ]
                }
            }
        }

        capture_resp = pod.es.search(index=INDEX_NAME, body=capture_query)
        if capture_resp["hits"]["total"] != 1:
            print(event_id)
            send_to_component(component_name="capture",
                              object_id="https://myresearch.institute/tracker/event/" + event_id)



 #if __name__ == "__main__":

    # temp = make_as2_profile("https://orcid.org/0000-0002-4372-870X")
    # print(json.dumps(temp, indent=2))
    # temp = make_as2_profile("https://orcid.org/0000-0002-4372-870X",
    #                  portal_name="github")
    # print(json.dumps(temp, indent=2))
    # temp = batch_user_portals("figshare")
    # print(json.dumps(temp, indent=2))

    # run_batch_portal("figshare")
    # run_user_portals("https://orcid.org/0000-0002-4372-870X")
