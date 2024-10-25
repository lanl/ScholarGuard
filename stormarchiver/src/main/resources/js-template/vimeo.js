var umbraState = {'idleSince':null};
var umbraVideoElements = document.getElementsByTagName('video');
for (var i = 0; i < umbraVideoElements.length; i++) {
	umbraVideoElements[i].play();
}
umbraState.idleSince = Date.now();


var UMBRA_USER_ACTION_IDLE_TIMEOUT_SEC = 10;


var umbraBehaviorFinished = function() {
        if (umbraState.idleSince != null) {
                var idleTimeMs = Date.now() - umbraState.idleSince;
                if (idleTimeMs / 1000 > UMBRA_USER_ACTION_IDLE_TIMEOUT_SEC) {
                        return true;
                }
        }
        return false;
}