FROM python:3.6-slim

RUN apt-get update && apt-get install -y gcc

WORKDIR /app
RUN cd /app
# Copy files required for setup
COPY setup.py LICENSE README.md /app/
RUN pip install -e .
RUN pip install uwsgi
COPY . /app
