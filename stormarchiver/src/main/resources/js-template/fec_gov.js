var umbraBehavior = {
	IDLE_TIMEOUT_SEC : 10,
	idleSince : null,
	alreadyClicked : {},

	intervalFunc : function() {
		var clickedSomething = false;
		var somethingLeftBelow = false;
		var somethingLeftAbove = false;
		var cssDownloadLinkSelector = "a[id^='id_csv']";

		var iframes = document.querySelectorAll("iframe");
		var documents = Array(iframes.length + 1);
		documents[0] = document;

		for (var i = 0; i < iframes.length; i++) {
			documents[i+1] = iframes[i].contentWindow.document;
		}

		for (var j = 0; j < documents.length; j++) {
			var clickDownloadLinkTargets = documents[j].querySelectorAll(cssDownloadLinkSelector);
			for (var i = 0; i < clickDownloadLinkTargets.length; i++) {
				var sourceName = clickDownloadLinkTargets[i].id.substring(7);
				var clickRadioButtonTargets = documents[j].querySelectorAll("input[name='" + sourceName + "']");

				if (clickRadioButtonTargets.length == 0) {
					if (clickDownloadLinkTargets[i].umbraClicked) {
						continue;
					}

					var mouseOverEvent = document.createEvent('Events');
					mouseOverEvent.initEvent("mouseover",true, false);
					clickDownloadLinkTargets[i].dispatchEvent(mouseOverEvent);
					clickDownloadLinkTargets[i].click();
					clickedSomething = true;
					this.idleSince = null;
					clickDownloadLinkTargets[i].umbraClicked = true;
				}
				else {
					for (var k = 0; k < clickRadioButtonTargets.length; ++k) {
						if (clickRadioButtonTargets[k].umbraClicked) {
							continue;
						}

						var where = this.aboveBelowOrOnScreen(clickRadioButtonTargets[k]);
						if (where == 0) {
							console.log("clicking on " + clickRadioButtonTargets[k]);
							
							var mouseOverEvent = document.createEvent('Events');
							mouseOverEvent.initEvent("mouseover",true, false);
							clickRadioButtonTargets[k].dispatchEvent(mouseOverEvent);
							clickRadioButtonTargets[k].click();
							mouseOverEvent = document.createEvent('Events');
							mouseOverEvent.initEvent("mouseover",true, false);
							clickDownloadLinkTargets[i].dispatchEvent(mouseOverEvent);
							clickDownloadLinkTargets[i].click();
							clickedSomething = true;
							this.idleSince = null;
							clickRadioButtonTargets[k].umbraClicked = true;


							break; 
						} else if (where > 0) {
							somethingLeftBelow = true;
						} else if (where < 0) {
							somethingLeftAbove = true;
						}
					}
				}

			}
		}

		if (!clickedSomething) {
			if (somethingLeftAbove) {
				window.scrollBy(0, -500);
				this.idleSince = null;
			} else if (somethingLeftBelow) {
				window.scrollBy(0, 200);
				this.idleSince = null;
			} else if (window.scrollY + window.innerHeight < document.documentElement.scrollHeight) {
			
				window.scrollBy(0, 200);
				this.idleSince = null;
			} else if (this.idleSince == null) {
				this.idleSince = Date.now();
			}
		}

		if (!this.idleSince) {
			this.idleSince = Date.now();
		}
	},

	start : function() {
		var that = this;
		this.intervalId = setInterval(function() {
			that.intervalFunc()
		}, 250);
	},

	isFinished : function() {
		if (this.idleSince != null) {
			var idleTimeMs = Date.now() - this.idleSince;
			if (idleTimeMs / 1000 > this.IDLE_TIMEOUT_SEC) {
				clearInterval(this.intervalId);
				return true;
			}
		}
		return false;
	},

	aboveBelowOrOnScreen : function(e) {
		var eTop = e.getBoundingClientRect().top;
		if (eTop < window.scrollY) {
			return -1; 
		} else if (eTop > window.scrollY + window.innerHeight) {
			return 1; 
		} else {
			return 0;
		}
	},
};

var umbraBehaviorFinished = function() {
	return umbraBehavior.isFinished()
};

umbraBehavior.start();





