# -*- coding: utf-8 -*-

from researcher_pod.store.user import Portal, User,\
    user_database, Role, roles_users
from researcher_pod.store.portal_config import PortalConfig
from researcher_pod import pod
from cryptography.fernet import Fernet
import pickle
from pickle import UnpicklingError

LOG = pod.log
app = pod.app
db = pod.db


def get_owner() -> User:
    """
    Finds and returns the owner of the pod. Assumes only one owner
    exists per pod.
    Since these tables are created only when the first request is made
    to the pod, it is possible the tracker jobs from celery will access
    this function before the tables are created. In this scenario, the
    ProgrammingError Exception thrown is captured and a None is returned.

    :return: (User|None) The owner or None.
    """
    from sqlalchemy.exc import ProgrammingError
    try:
        return pod.db.session.query(User). \
            join(roles_users). \
            join(Role). \
            filter(Role.name == "owner").first()
    except ProgrammingError:
        return None


def user_authorised_for_settings_page() -> bool:
    """
    Determines if a user is authorized to visit the settings page to
    modify portal configurations. A user is allowed under 2 conditions:
    1) A fresh install of the pod and no user with the role "owner" is
    found in the db.
    2) A user with the role "owner" exists and is logged in.
    All other users are not authorized.

    :return: (bool) True if authorised, else False.
    """
    from flask_security import current_user
    owner = get_owner()
    try:
        cu_id = current_user.id
    except AttributeError:
        cu_id = ""

    if not owner and not bool(cu_id):
        # no owner has been created in the db.
        # so this is the first a user is trying to access
        # the settings page.
        return True

    if owner and owner.id == cu_id:
        # if owner exists and the owner is logged in
        return True
    return False


def get_lower_upper_portal_name(portal_name):
    """
    Converts the portal_name to lower and upper cases.
    A utility function used for mainly authentication modules.

    :param portal_name: (str) The portal name to convert.
    :return: tuple(str, str) the portal name in lower case and upper case.
    """

    if not bool(portal_name):
        # TODO: Error Page with message
        return None, None

    return portal_name.lower(), portal_name.upper()


def get_portal_from_db(user_id=None,
                       portal_name=None,
                       username=None) -> Portal:
    """
    Searches for and returns the Portal db object corresponding to the
    parameters provided. The portal_name parameter is required, along with
    one of user_id or username.

    Mainly used to check if the user logging in already exists. If exists,
    the existing user is logged in, else, a new user and portal
    will be created.

    :param user_id: (int) The user id of an existing user.
    :param portal_name: (str) The name of the portal.
    :param username: (str) The username of the user in the corresponding
    portal.
    :return: (Portal) The portal if exists, None otherwise.
    """

    if not portal_name:
        return None

    if not user_id and not username:
        return None
    if user_id:
        portal: Portal = Portal.query\
            .filter(Portal.user_id == user_id,
                    PortalConfig.portal_name == portal_name,
                    PortalConfig.id == Portal.portal_config_id).all()
    else:
        portal: Portal = Portal.query\
            .filter(Portal.portal_username == username,
                    PortalConfig.portal_name == portal_name,
                    PortalConfig.id == Portal.portal_config_id).all()

    if len(portal) > 1:
        # TODO: multiple portals for the same user... can the pod support that?
        LOG.debug(
            "Multiple portals for the same user found."
            " Returning first found...")
        return portal[0]

    if len(portal) == 1:
        return portal[0]


def get_token_secret_for_portal(portal: Portal):
    """
    Gets, decrypts, and returns the OAuth Token and Secret from the Portal.

    :param portal: (Portal) the portal instance in db.
    :return: Tuple(str|dict, str) The token and secret. The token for oauth2
    is a dict while it is a str for others.
    """

    from cryptography.fernet import InvalidToken
    token = None
    secret = None
    if not portal or not isinstance(portal, Portal):
        return token, secret
    if not portal.portal_config.oauth_token or \
            not portal.portal_config.oauth_secret:
        return token, secret

    LOG.debug("OAuth Token found in db.")
    try:
        token = decrypt(portal.portal_config.oauth_token)
        secret = decrypt(portal.portal_config.oauth_secret)
    except InvalidToken:
        pass
    return token, secret


def get_user_profile(db_portal: Portal=None,
                     oauth=None,
                     portal_name=None,
                     token=None) -> dict:
    """
    Constructs and returns (as a dict) a user's profile information from
    either the database (:param: db_portal),
    or using the portal's API (:param: oauth),
    or using the metadata available in an oauth token (:param: token)

    When the oauth object is provided, the portal's API response is retrieved
    from using the user profile url provided in the config file. Then,
    the appropriate function for the portal is invoked to map the API response
    to the user profile object.

    :param db_portal: (Portal) The portal from db.
    :param oauth: (OAuthSession1|OAuthSession2) The oauth session object.
    :param portal_name: (str) the portal name.
    :param token: (dict) The token received after successful oauth.
    :return: (dict) the user profile dict object.
    """

    profile = {}
    if not db_portal and not oauth:
        LOG.debug("noauth tokens found.")

    elif db_portal and isinstance(db_portal, Portal):
        LOG.debug("getting tokens from saved portal in db.")
        user = User.query.filter_by(id=db_portal.user_id).first()
        profile["token"], profile["secret"] = get_token_secret_for_portal(
            db_portal)
        profile["username"] = db_portal.portal_username
        profile["full_name"] = user.full_name
        profile["portal_user_id"] = db_portal.portal_user_id
        profile["email"] = user.email

    elif oauth and bool(portal_name) and \
            app.config.get("%s_USER_PROFILE_URL" % portal_name.upper()):

        LOG.debug("Getting auth info from session.")
        response = oauth.get(
            app.config.get("%s_USER_PROFILE_URL" % portal_name.upper()))
        LOG.debug("RESPONSE: %s" % response.status_code)
        try:
            profile = getattr(
                __import__("researcher_pod.user.utils",
                           fromlist=["get_user_profile"]),
                "get_%s_user_profile" % portal_name.lower())(response)
        except AttributeError:
            pass
    elif token:
        try:
            profile = getattr(
                __import__("researcher_pod.user.utils",
                           fromlist=["get_user_profile"]),
                "get_%s_user_profile_from_token" % portal_name.lower())(token)
        except AttributeError:
            pass
    else:
        LOG.debug("No profile found! db_portal: {}}\noauth: {}\nportal_name: "
                  "{}".format(db_portal, oauth, portal_name))
    return profile


def encrypt(data) -> bytes:
    """
    Encrypts the data using the secret set. Converts string to bytes
    and pickles dict before encrypting.

    Mainly used for storing OAuth tokens and secrets in the db.

    :param data: (bytes|str|dict) The data to encrypt.
    :return: (bytes) The encrypted data.
    """
    if not bool(data):
        return
    if isinstance(data, str):
        data = data.encode("utf8")
    elif isinstance(data, dict):
        data = pickle.dumps(data)

    if not isinstance(data, bytes):
        return
    f = Fernet(app.config.get("SECRET_KEY"))
    return f.encrypt(data)


def decrypt(data):
    """
    Decrypts the data that was encrypted by :func: `encrypt`.
    If the decrypted data is a pickled dict, then it is
    unpickled. Else returned as bytes.
    :param data: (bytes|str) The encrypted data.
    :return: (bytes|dict) The decrypted data.
    """

    if not bool(data):
        return
    f = Fernet(app.config.get("SECRET_KEY"))
    if isinstance(data, str):
        data = data.encode("utf8")
    if not isinstance(data, bytes):
        return
    dec = f.decrypt(data)
    try:
        ret = pickle.loads(dec)
        if isinstance(ret, dict):
            return ret
    except UnpicklingError:
        pass
    return dec


def get_save_user_profile(user_profile: dict=None,
                          portal_name: str=None,
                          current_user_id: int=None,
                          token: bytes=None,
                          secret: bytes=None) -> (User, Portal):
    """
    Creates user in db (if needed) and updates the portal information
    for the user as needed. This function determines the role
    of the user (owner or visitor).

    The function checks if the user with the given parameters already
    exists, and returns that user without creating a new one if the user
    exists.

    :param user_profile: (dict) the user profile from :func:
    `get_user_profile`.
    :param portal_name: (str) the name of the portal.
    :param current_user_id: (int) the user id of the currently logged in user.
    :param token: (str) the oauth token
    :param secret: str) the oauth secret.
    :return: tuple(User, Portal) the user created in the db and the portal
    information.
    """
    user = None
    existing_portal: Portal = get_portal_from_db(
        username=user_profile.get("username"),
        portal_name=portal_name
    )

    # creating a new owner
    if should_create_owner(current_user_id):
        LOG.debug("Creating new owner")
        user = create_user_in_db(user_id=user_profile.get("portal_user_id"),
                                 email=user_profile.get("email"),
                                 full_name=user_profile.get("full_name"),
                                 password=token,
                                 roles=["owner", "admin"])
        LOG.debug("User created %s." % user.id)
    elif should_create_new_user(user_profile,
                                current_user_id=current_user_id):
        LOG.debug("Creating new visitor")
        user = create_user_in_db(user_id=user_profile.get("portal_user_id"),
                                 email=user_profile.get("email"),
                                 full_name=user_profile.get("full_name"),
                                 password=token,
                                 roles=["visitor"])
        LOG.debug("User created %s." % user.id)

    if not user and current_user_id:
        LOG.debug("creating user object from current_user_id")
        user = User.query.filter_by(id=current_user_id).first()
    elif not user and existing_portal:
        LOG.debug("creating user object from existing_portal")
        user = User.query.filter_by(id=existing_portal.user_id).first()

    # updating an existing user profile
    # TODO: check if the tokens need to be updated in an existing profile.
    if should_update_existing_portal(user_portal=existing_portal):
        if not user:
            LOG.debug("ERROR!! unable to find user in db.")

        user_updated = add_portal_for_user(user=user,
                                           user_profile=user_profile,
                                           portal_name=portal_name,
                                           token=token,
                                           secret=secret)
        if user_updated:
            LOG.debug("Portal updated.")
        else:
            LOG.debug("Error updating user profile.")
            return None, None

    existing_portal: Portal = get_portal_from_db(
        username=user_profile.get("username"),
        portal_name=portal_name
    )
    return user, existing_portal


def create_user_in_db(user_id: str=None,
                      email: str=None,
                      full_name: str=None,
                      profile_image: str=None,
                      password: str=None,
                      roles: [str]=None):
    """
    Creates the user in the db with the provided parameters.
    The password provided will be encrypted before saving to db.

    :param user_id: (str) the orcid user_id
    :param email: (str) the email address of the user.
    :param full_name: (str) the full name of the user.
    :param password: (str) the password of the user.
    :param roles: (list(str)) the roles for the user.
    :return: (User) the user created.
    """
    LOG.debug("Creating new user/owner")
    if not isinstance(roles, list):
        roles = ["visitor"]
    if not bool(user_id) or \
            not bool(email) or \
            not bool(full_name) or \
            not bool(password):
        return None
    if "https://orcid.org/" not in user_id:
        user_id = "https://orcid.org/{}".format(user_id)
    user_database.create_user(id=user_id,
                              email=email,
                              full_name=full_name,
                              profile_image=profile_image,
                              password=encrypt(password),
                              roles=roles)
    db.session.commit()
    return user_database.get_user(email)


def add_portal_for_user(user: User=None,
                        user_profile: dict=None,
                        portal_name: str=None,
                        token: bytes=None,
                        secret: bytes=None) -> bool:
    """
    Creates a portal for the user in the db.

    :param user: (User) the existing user's db object.
    :param user_profile: (dict) the user profile information from
    :func:`get_user_profile`.
    :param portal_name: (str) the portal name.
    :param token: (bytes) the oauth token.
    :param secret: (bytes) the oauth secret.
    :return: (bool) True if the portal is successfully created, else False.
    """
    if not bool(user) or not isinstance(user, User):
        LOG.debug("ERROR!! unable to find user in db.")
        return False
    if not user_profile.get("username"):
        LOG.debug("Error!! unable to find the portal's username in"
                  " user_profile")
        return False
    if not bool(portal_name) or not bool(token):
        LOG.debug("Error!! Both portal_name and token are required")
        return False

    LOG.debug("Updating portal profile of user")

    portal_config = get_portal_config_by_name(portal_name)
    if token and secret:
        portal_config.oauth_token = encrypt(token)
        portal_config.oauth_secret = encrypt(secret)

    portal = Portal()
    portal.user_id = user.id
    portal.portal_config_id = portal_config.id
    portal.portal_username = user_profile.get("username")
    portal.portal_email = user_profile.get("email")
    portal.portal_user_id = user_profile.get("portal_user_id")

    db.session.add(portal)
    db.session.commit()
    return True


def add_user_portal(user: User=None,
                    user_profile: dict=None,
                    portal_name: str=None):
    """
    Add a portal for a specified user
    """
    if not bool(user) or not isinstance(user, User):
        LOG.debug("ERROR!! unable to find user in db.")
        return False
    if portal_name not in ["personal_website"]:
        if not user_profile.get("username") and \
                not user_profile.get("portal_user_id"):
            LOG.debug("Error!! unable to find the portal's username or user id"
                      " in user_profile")
            return False
    if not bool(portal_name):
        LOG.debug("Error!! portal_name is required")
        return False
    portal_config = get_portal_config_by_name(portal_name)

    LOG.debug("Creating portal profile for user")
    portal = Portal()
    portal.user_id = user.id
    portal.portal_config_id = portal_config.id
    portal.portal_username = user_profile.get("username")
    portal.portal_email = user_profile.get("email")
    portal.portal_url = user_profile.get("portal_url")
    portal.portal_user_id = user_profile.get("portal_user_id")

    db.session.add(portal)
    db.session.commit()
    return True


def should_create_owner(current_user_id: int) -> bool:
    """
    Determines if an owner should be created in the db. Assumes
    only one owner can be created per pod.

    If there are 0 users in the db, then the first user to be created
    is the owner. On all other circumstances, no owner should be created.

    :param current_user_id: (int) the user id of the currently logged in user.
    :return: (bool) True, if an owner should be created, False otherwise.
    """
    users = User.query.all()
    if not bool(current_user_id) and len(users) == 0:
        LOG.debug("owner should be created")
        return True
    LOG.debug("owner already created")
    return False


def get_portal_config_by_name(portal_name: str=None) -> PortalConfig:
    """
    Query database by portal name

    :param portal_name: (str) name of the portal
    :return: (PortalConfig) object of the portal queried
    """
    portal_config = PortalConfig.query.filter_by(
        portal_name=portal_name
    ).first()

    return portal_config


def should_create_new_user(user_profile: dict,
                           current_user_id: int=None) -> bool:
    """
    Determines if a new user must be created in the db.

    * A new user will be created if no portal information is
    found in the db for the username and portal user id provided.
    * A new user need not be created if a user is currently
    logged in (A portal will be added to this user)
    * New user will not created if no user profile information
    is provided as a parameter.

    :param user_profile: (dict) the user profile obtained from
    :func:`get_user_profile`.
    :param current_user_id: (int) the current logged in user's id
    :return: (bool) True if a new user must be created, else False.
    """
    if not current_user_id:
        current_user_id = ""
    if not bool(user_profile) or len(current_user_id) > 0:
        return False
    portal = Portal.query.filter_by(
        portal_username=user_profile.get("username"),
        portal_user_id="https://orcid.org/{}"
        .format(user_profile.get("portal_user_id"))).first()
    if not portal:
        LOG.debug("new user must be created")
        return True
    LOG.debug("new user need not be created")
    return False


def bulk_create_users(csv_file=None):
    """
    Read from a CSV a list of users and their associated portal
    ids. Create User and portals if not found, otherwise update
    user information and portals.
    """
    import csv
    import os

    if not csv_file:
        csv_file = os.path.join(os.path.dirname(__file__),
                                "../../tests/data/research_portal_data.csv")

    with open(csv_file, 'r') as f:
        reader = csv.reader(f)
        headers = next(reader)
        for row in reader:
            print(row)
            id_ = row[0]
            roles = row[1]
            roles = roles.split(",")
            full_name = row[2]
            profile_image = row[3]
            # needs app context
            with pod.app.app_context():
                user = User.query.filter_by(id=id_).first()
                if not user:
                    print("Creating User")
                    user: User = create_user_in_db(user_id=id_,
                                                   email=id_,
                                                   profile_image=profile_image,
                                                   full_name=full_name,
                                                   password="123",
                                                   roles=roles)
                else:
                    print("Updating User")
                    db.session.query(User).filter(User.id == user.id).\
                        update({"profile_image": profile_image})
                    db.session.commit()
                iter_num = 3
                while iter_num != len(row) - 1:
                    print(headers[iter_num + 1], headers[iter_num + 2])
                    portal_name = headers[iter_num + 1].split("_", 1)[0]
                    portal_username = row[iter_num + 1]
                    portal_user_id = row[iter_num + 2]
                    profile = {"username": portal_username,
                               "portal_user_id": portal_user_id,
                               "email": ""}

                    portal: Portal = Portal.query\
                        .filter(Portal.user_id == user.id,
                                PortalConfig.portal_name == portal_name,
                                PortalConfig.id == Portal.portal_config_id)\
                        .first()

                    if not portal:
                        add_user_portal(user=user,
                                        user_profile=profile,
                                        portal_name=portal_name
                                        )
                    iter_num += 2


def bulk_create_portals():
    """
    Read from a CSV a list of users each with a username, user_id, and also
    a portal_url for a specified portal. Create Portal if not found.
    If a username, user_id, and portal_url already exist for a portal config,
    skip.
    """
    import csv
    import os
    filename = os.path.join(os.path.dirname(__file__),
                            "../../tests/data/multi_portal_users.csv")
    with open(filename, 'r') as f:
        reader = csv.reader(f)
        # skip headers
        headers = next(reader)
        print(headers)
        for row in reader:
            # print(row)
            id_ = row[0]
            portal_name = row[1]
            # needs app context
            with pod.app.app_context():
                user = User.query.filter_by(id=id_).first()
                if not user:
                    print("User for {} not found. Skipping.".format(id_))
                    continue

                portal_username = row[2]
                portal_user_id = row[3].replace("\"", "")

                portal_url = row[4]
                profile = {"username": portal_username,
                           "portal_user_id": portal_user_id,
                           "portal_url": portal_url,
                           "email": ""}
                # print(profile)
                portal: Portal = Portal.query\
                    .filter(Portal.user_id == user.id,
                            Portal.portal_url == portal_url,
                            Portal.portal_username == portal_username,
                            PortalConfig.portal_name == portal_name,
                            PortalConfig.id == Portal.portal_config_id)\
                    .first()

                if not portal:
                    add_user_portal(user=user,
                                    user_profile=profile,
                                    portal_name=portal_name
                                    )
                else:
                    print("Found portal for {}.".format(id_))


def collect_all_activities():
    """
    Helper function to run trackers for every saved portal.
    """
    from importlib import import_module
    with pod.app.app_context():
        portals = Portal.query.filter(
            PortalConfig.id == Portal.portal_config_id)\
            .all()
        for portal in portals:
            config: PortalConfig = PortalConfig.query\
                .filter_by(id=portal.portal_config_id).first()
            module = import_module('researcher_pod.tracker.{}'
                                   .format(config.portal_name))
            print(config.portal_name)
            if config.portal_name not in ["figshare"]:
                try:
                    module.run(portal.user_id)
                except Exception:
                    continue


def should_update_existing_portal(
        user_portal: Portal=None) -> bool:
    """
    Determines if the user's portal should be updated
    with new information or not.

    :param user_portal: (Portal) the user portal.
    :return: (bool) True if the portal should be updated, else False
    """
    if not bool(user_portal):
        LOG.debug("user portal must be updated.")
        return True
    LOG.debug("user portal need not be updated.")
    return False


def should_login_user(user_portal: Portal=None,
                      current_user_id: int=None) -> bool:
    """
    Determines if the user should be logged in. If no user is
    currently logged in and the user_portal exists, then the user
    can be logged in. Else, not.
    :param user_portal: (Portal) the user portal from db.
    :param current_user_id: (int) the user id of the currently logged in user.
    :return: (bool) True, if the user should be logged in, False otherwise.
    """
    if not isinstance(user_portal, Portal):
        LOG.debug("user_portal not of type Portal")
        return False
    if user_portal and not current_user_id:
        LOG.debug("User should be logged in.")
        return True
    LOG.debug("User already logged in.")
    return False


def get_twitter_user_profile(response) -> dict:
    """
    Maps the response from Twitter API for a user to the
    user_profile object.

    :param response: (Response) the response from Twitter API.
    :return: (dict) the user's profile information.
    """
    profile = {}
    user_profile = response.json()
    profile["username"] = user_profile.get("screen_name")
    profile["full_name"] = user_profile.get("name")
    profile["portal_user_id"] = user_profile.get("id_str")
    profile["email"] = user_profile.get("email") or \
        user_profile.get("screen_name")
    profile["portal_url"] = "https://twitter.com/" +\
                            user_profile.get("screen_name")

    LOG.debug("profile: %s" % profile)
    return profile


def get_github_user_profile(response) -> dict:
    """
    Maps the response from GitHub API for a user to the
    user_profile object.

    :param response: (Response) the response from GitHub API.
    :return: (dict) the user's profile information.
    """
    profile = {}
    user_profile = response.json()

    profile["username"] = user_profile.get("login")
    profile["full_name"] = user_profile.get("name")
    profile["portal_user_id"] = user_profile.get("id")
    profile["email"] = user_profile.get("email") or user_profile.get("login")
    profile["portal_url"] = user_profile.get("html_url")

    LOG.debug("profile: %s" % profile)
    return profile


def get_orcid_user_profile_from_token(token: dict):
    """
    Maps the information from the ORCiD OAuth token to the
    user_profile object.

    :param token: (dict) the oauth response token from ORCiD.
    :return: (dict) the user's profile information.
    """

    profile = {}
    profile["token"] = token
    profile["username"] = token.get("name")
    profile["email"] = profile["username"]
    profile["full_name"] = profile["username"]
    profile["portal_user_id"] = token.get("orcid")

    return profile
