from importlib import import_module

"""

Running 1 actor_id -> N portals

id1 | portal_name1
id1 | portal_name2
id1 | portal_name3
id1 | portal_name4

1. Get the AS2 message
2. Iterate through the users and their portals
3. if portal not in batch_apis list start immediately
   else add to a list
4. Iterate the the batch portals found and execute tracker
   with list of saved users


Controller:
"""


def queue_tasks(message):
    """
    Task queueing logic for ingesting an AS2 message to then track users.
    Message received via LDN inbox.
    """
    batch_apis = ["figshare", "blogger", "wordpress"]
    batch_queue = {}
    users = message.get("event", {}).get("object", {}).get("describes", [])
    ldn_inbox_url = message.get("event", {}).get("to")
    event_base_url = message.get("event", {}).get("tracker:eventBaseUrl")
    if not ldn_inbox_url or not event_base_url:
        # TODO: error message
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
            if portal_name in batch_apis:
                batch_queue.setdefault(portal_name, {})
                batch_queue[portal_name].setdefault("users", [])
                batch_queue[portal_name].setdefault("portal_name", portal_name)
                batch_queue[portal_name].setdefault("ldn_inbox_url",
                                                    ldn_inbox_url)
                batch_queue[portal_name].setdefault("event_base_url",
                                                    event_base_url)
                batch_queue[portal_name]["users"].append(portal_user)
            else:
                tracker = import_module('artifact_tracker.tracker.{}'
                                        .format(portal_name))
                #tracker.run.delay(portal_name=portal_name,
                #                  users=[portal_user],
                #                  ldn_inbox_url=ldn_inbox_url,
                #                  event_base_url=event_base_url)
                # synchronous testing
                tracker.run(portal_name=portal_name,
                            users=[portal_user],
                            ldn_inbox_url=ldn_inbox_url,
                            event_base_url=event_base_url)
    # batch users in api request
    for portal_name in batch_queue:
        config = batch_queue.get(portal_name, {})
        print("BATCHQ CONFIG")
        print(config)
        tracker = import_module('artifact_tracker.tracker.{}'
                                .format(portal_name))
        tracker.run.delay(**config)
        # synchronous testing
        # tracker.run(**config)

    return True
