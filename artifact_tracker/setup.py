# -*- coding: utf-8 -*-
# ! /usr/bin/env python3

from setuptools import setup, Command, find_packages
from setuptools.command.install import install
import os
import glob
import shutil

here = os.path.abspath(os.path.dirname(__file__))


class PostInstallCommand(install):
    """
    Post installation things to be executed.
    """
    def run(self):
        install.run(self)
        import os
        import base64
        secret_key = base64.urlsafe_b64encode(os.urandom(32))
        salt = base64.urlsafe_b64encode(os.urandom(16))

        secret_file_name = os.path.join(
            os.path.dirname(os.path.realpath(__file__)), "secrets", "secrets")

        lines = []
        if os.path.isfile(secret_file_name):
            with open(secret_file_name, "r", encoding="utf8") as sf:
                lines = sf.readlines()

        if not len(lines) == 2:
            with open(secret_file_name, "w+", encoding="utf8") as sf:
                sf.write("%s\n%s" % (
                    secret_key.decode("utf8"), salt.decode("utf8")))


class BetterClean(Command):
    """Custom clean command to remove other stuff from project root."""
    user_options = []

    def initialize_options(self):
        pass

    def finalize_options(self):
        pass

    @staticmethod
    def handle_remove_errors(*args):
        print("Issue removing '{}' (probably does not exist), skipping..."
              .format(args[1]))

    def run(self):
        egg_info = glob.glob('*.egg-info')

        for entry in egg_info:
            print("removing " + entry)
            shutil.rmtree(entry)

        shutil.rmtree('build', onerror=BetterClean.handle_remove_errors)
        shutil.rmtree('dist', onerror=BetterClean.handle_remove_errors)


def test_suite():
    import unittest
    test_loader = unittest.TestLoader()
    tests = test_loader.discover("tests", pattern="test_*.py")
    return tests


with open("README.md") as f:
    readme = f.read()

with open("LICENSE") as f:
    license = f.read()


setup(
    name="artifact_tracker",
    version="0.1-dev",
    description="Artifact trackers for various productivity portals",
    long_description=readme,
    author="Grant Atkins",
    url="https://github.com/oduwsdl/scholarly-orphans-trackers/",
    license=license,
    zip_safe=False,
    packages=find_packages(exclude=("tests", "docs")),
    include_package_data=True,
    install_requires=[
        "Flask",
        "flask-sqlalchemy",
        "Flask-WTF",
        "pymysql",
        "cryptography",
        "pyyaml",
        "celery[redis]",
        "requests-oauthlib",
        "rdflib>=4.2.1",
        "rdflib-jsonld>=0.4.0",
        "lxml",
        "blinker",
        "feedparser",
        "sickle>=0.6.3"
    ],
    tests_require=["sqlalchemy_utils"],
    test_suite="setup.test_suite"
)
