@echo off
echo Cleaning up for 10 seconds...
rem use ping to cause a 10 second delay.
PING 1.1.1.1 -n 1 -w 10000 >NUL
echo All done cleaning up.