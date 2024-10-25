var umbraState = {'idleSince':null};
var umbraIntervalID = setInterval(umbraScrollInterval,50);
var umbraAlreadyClicked = {};
function umbraScrollInterval() {

   if(window.scrollY + window.innerHeight < document.documentElement.scrollHeight) {
       umbraScroll();
       umbraState.idleSince=null;
   }
   else {
	var videoBoxes = document.querySelectorAll("div#vid_box a");
	var clickedVideo = false;

	for(i=0;i<videoBoxes.length;i++) {
	    if(!(videoBoxes[i] in umbraAlreadyClicked)){
		videoBoxes[i].click();
		umbraState.idleSince=null;
		umbraAlreadyClicked[videoBoxes[i]]=true;
		clickedVideo=true;
	    }
	}

	if(!clickedVideo && umbraState.idleSince==null) {
	    umbraState.idleSince=Date.now();
	}
   }

}

function umbraScroll() {
    window.scrollBy(0,50);
}



var UMBRA_USER_ACTION_IDLE_TIMEOUT_SEC = 10;


var umbraBehaviorFinished = function() {
    if (umbraState.idleSince != null) {
	var idleTimeMs = Date.now() - umbraState.idleSince;
	if (idleTimeMs / 1000 > UMBRA_USER_ACTION_IDLE_TIMEOUT_SEC) {
            clearInterval(umbraIntervalID);
	    return true;
	}
    }
    return false;
}

