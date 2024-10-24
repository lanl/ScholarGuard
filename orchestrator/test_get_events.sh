#!/usr/bin/env bash

source ~/venv/orchestrator/bin/activate

cd ~/dropbox/Projects/orchestrator/web/
pip install -e .

export RESEARCHER_POD_CONFIG="/Users/harihar/dropbox/Projects/orchestrator/conf/config_standalone.yaml"
export RESEARCHER_POD_SECRETS="/Users/harihar/dropbox/Projects/orchestrator/secrets/secrets"
# Don't actually run past this script
exit 0

python -c "from researcher_pod.tracker.twitter import run; run();"

python -c "from researcher_pod.pod.utils import make_as2_profile; import json; print(json.dumps(make_as2_profile('https://orcid.org/0000-0002-4372-870X'), indent=2));"

# Manually run all portals (or specified portal) for user_id
python3 -c "from researcher_pod.pod.utils import run_user_portals; run_user_portals('https://orcid.org/0000-0002-4372-870X');"

python3 -c "from researcher_pod.pod.utils import run_batch_portal; run_batch_portal('figshare');"
"researcher_pod.pod.utils.run_user_portals('https://orcid.org/0000-0002-2668-4821')"

python3 -c "from researcher_pod.ldn.inbox import is_duplicate_activity; is_duplicate_activity(object_id='', event_published='');"
"researcher_pod.pod.utils.run_user_portals('https://orcid.org/0000-0002-2668-4821')"

# Manually update tracker state for user_id/portal_name
python3 -c "from researcher_pod.ldn.inbox import update_tracker_state; update_tracker_state(user_id='https://orcid.org/0000-0003-1891-1112', portal_name='github', prov_generated_at='2018-11-05T21:08:58Z', last_token='W/\"1f8c60fd0295dcdd85c430edef9f7114\"')"

# Send message to individual component - specify event message id
python3 -c "from researcher_pod.ldn.inbox import send_to_component; send_to_component(component_name='capture', object_id='https://myresearch.institute/tracker/event/0cb7bb8d4b6f4dcd8faf61281e0f5617')"
