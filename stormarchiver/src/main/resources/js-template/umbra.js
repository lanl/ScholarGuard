class UmbraBehavior {

    constructor(actions) {
        this.IDLE_TIMEOUT_SEC = 10;
        this.actions = actions;
        this.alreadyDone = [];
        this.idleSince = null;
        this.intervalId = null;
        this.intervalTimeMs = {{interval or 300}};
        this.index = 0;
    }

    simpleIntervalFunc() {
      
        var k = this.index;
        var selector = this.actions[k].selector;
        var repeatSameElement = this.actions[k].repeatSameElement ? this.actions[k].repeatSameElement : false;
        var firstMatchOnly = this.actions[k].firstMatchOnly ? this.actions[k].firstMatchOnly : false;
        var action = this.actions[k].do ? this.actions[k].do : 'click';
        var closeSelector = this.actions[k].closeSelector ? this.actions[k].closeSelector : null;
        var didSomething = false;
        var somethingLeftAbove = false;
        var somethingLeftBelow = false;

        var documents = [];
        documents[0] = document;

        var iframes = document.querySelectorAll("iframe");
        var iframesLength = iframes.length;
        for (var i = 0; i < iframesLength; i++) {
            try {
                documents.push(iframes[i].contentWindow.document);
            } catch (e) {
               
                
            }
        }

        var documentsLength = documents.length;
        for (var j = 0; j < documentsLength; j++) {
            if (closeSelector) {
                var closeTargets = documents[j].querySelectorAll(closeSelector);
                for (var i = 0; i < closeTargets.length; i++) {
                    if (this.isVisible(closeTargets[i])) {
                        closeTargets[i].click();
                        didSomething = true;
                        break;
                    }
                }
            }

            if (firstMatchOnly) {
                var doTargets = [ documents[j].querySelector(selector) ];
            } else {
                var doTargets = documents[j].querySelectorAll(selector);
            }

            var doTargetsLength = doTargets.length;
            if (!(doTargetsLength > 0)) {
                continue;
            }

            for ( var i = 0; i < doTargetsLength; i++) {
                if (!repeatSameElement && this.alreadyDone.indexOf(doTargets[i]) > -1) {
                    continue;
                }
                if (!this.isVisible(doTargets[i])) {
                    continue;
                }
                var where = this.aboveBelowOrOnScreen(doTargets[i]);
                if (where == 0) {
                    this.doTarget(doTargets[i], action);
                    didSomething = true;
                    break;
                } else if (where > 0) {
                    somethingLeftBelow = true;
                } else if (where < 0) {
                    somethingLeftAbove = true;
                }
            }
        }

        if (!didSomething) {
            if (somethingLeftAbove) {
                window.scrollBy(0, -500);
                this.idleSince = null;
            } else if (somethingLeftBelow || ( (window.scrollY + window.innerHeight) < document.documentElement.scrollHeight)) {
                window.scrollBy(0, 200);
                this.idleSince = null;
            } else if (this.idleSince == null) {
                this.idleSince = Date.now();
            }
        }

        if (!this.idleSince) {
            this.idleSince = Date.now();
        } else {
            var idleTimeMs = Date.now() - this.idleSince;
            if ((idleTimeMs / 1000) > (this.IDLE_TIMEOUT_SEC - 1) && (this.index < (this.actions.length - 1))) {
                
                this.index += 1;
                this.idleSince = null;
                window.scroll(0,0);
            }
        }
    }

    aboveBelowOrOnScreen(elem) {
        var eTop = elem.getBoundingClientRect().top;
        if (eTop < window.scrollY) {
            return -1; 
        } else if (eTop > window.scrollY + window.innerHeight) {
            return 1; 
        } else {
            return 0; 
        }
    }

    isVisible(elem) {
        return elem && !!(elem.offsetWidth || elem.offsetHeight || elem.getClientRects().length);
    }

    doTarget(target, action) {
        
        var mouseOverEvent = document.createEvent("Events");
        mouseOverEvent.initEvent("mouseover", true, false);
        target.dispatchEvent(mouseOverEvent);

        if (action == "click") {
            target.click();
        } 

        this.alreadyDone.push(target);
        this.idleSince = null;
    }

    start() {
        var that = this;
        this.intervalId = setInterval(function() {
            that.simpleIntervalFunc()
        }, this.intervalTimeMs);
    }

    isFinished() {
        if (this.idleSince != null) {
            var idleTimeMs = Date.now() - this.idleSince;
            if (idleTimeMs / 1000 > this.IDLE_TIMEOUT_SEC) {
                clearInterval(this.intervalId);
                return true;
            }
        }
        return false;
    }
}

var umbraBehavior = new UmbraBehavior( {{actions|json}} );
var umbraBehaviorFinished = function() {
    return umbraBehavior.isFinished();
};

umbraBehavior.start();