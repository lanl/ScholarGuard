grant all privileges on `researcher_pod`.* to 'researcher_pod'@'localhost' identified by 'R3s3arc3r_P0d';
grant all privileges on `researcher_pod_test`.* to 'researcher_pod'@'localhost' identified by 'R3s3arc3r_P0d';
drop database if exists researcher_pod;
create database researcher_pod;