//
// This file contains implementations of friendly wrappers
//

///<reference path='richer_contracts.ts'/>

interface HTMLElement {
    removeAllChildren() : void;
    appendChildren(nodes: Node[]) : void;
    replaceChildrenWith(node: Node) : void;
}    

HTMLElement.prototype.removeAllChildren = function() : void {
    while (this.children.length > 0) {
        this.removeChild(this.children[0]);
    }    
}

HTMLElement.prototype.appendChildren = function(nodes : Node[]) : void {    
    nodes.forEach(x => this.appendChild(x));
}

HTMLElement.prototype.replaceChildrenWith = function(node: Node) : void {    
    this.removeAllChildren();
    this.appendChild(node);
}

Window.prototype.androidPostReplyToPromise = function (replyToJson : string) {
    console?.log("androidPostReplyToPromise("+replyToJson+")");

    let decoded : AndroidReply = JSON.parse(replyToJson);
    console?.log("androidPostReplyToPromise decoded="+decoded);

    let noAutoClean = window.promiseNoAutoClean.has(decoded.PromiseId);

    let resolved = window.promiseResolvedCallBacks.get(decoded.PromiseId);
    
    if (resolved === undefined) {
        console?.log("androidPostReplyToPromise resolved is undefined");
        return;
    }

    let rejected = window.promiseRejectedCallBacks.get(decoded.PromiseId);
   
    if (rejected === undefined) {
        console?.log("androidPostReplyToPromise rejected is undefined");
        return;
    }
    
    console?.log("androidPostReplyToPromise IsCanc="+decoded.IsCancellation+" noAutoClean="+noAutoClean + " barcode="+decoded.Barcode);
    
    if (!noAutoClean) {
        window.promiseResolvedCallBacks.delete(decoded.PromiseId);
        window.promiseRejectedCallBacks.delete(decoded.PromiseId);
    }

    if (decoded.IsCancellation === false) {
        console?.log("androidPostReplyToPromise not cancel");
        resolved(decoded.Barcode);
        return;
    }

    if (decoded.IsCancellation === true) {
        console?.log("androidPostReplyToPromise cancel");
        rejected(decoded.Barcode);
        return;
    }

    console?.log("androidPostReplyToPromise unknown");
};

Window.prototype.promiseDisableAutoClean = function (promiseId : string) {
    window.promiseNoAutoClean.add(promiseId);
}

Window.prototype.promiseClean = function (promiseId : string) {
    window.promiseNoAutoClean.delete(promiseId);
    window.promiseResolvedCallBacks.delete(promiseId);
    window.promiseRejectedCallBacks.delete(promiseId);
}

Window.prototype.scanQr = function(layoutData : contracts.LayoutStrategy) : Promise<string> {
    let self = this;

    return new Promise(function (resolve,reject) {        
        if (self.Android === undefined) {
            //dev friendly polyfill
            let result = window.prompt("[blocking] scan QR:");

            if (result == null) {
                return reject("user cancelled window.prompt()");
            }

            return resolve(result);            
        }

        //calls android host
        let promiseId = (self.nextPromiseId++).toString();
        
        self.promiseResolvedCallBacks.set(promiseId, (x:string) => resolve(x));
        self.promiseRejectedCallBacks.set(promiseId, (x:string) => reject(x));
        
        console?.log("calling self.Android.requestScanQr");

        self.Android.requestScanQr(promiseId, false, JSON.stringify(layoutData));
    });    
}

Window.prototype.scanQrCancellable = function(layoutData : contracts.LayoutStrategy) : [Promise<string>, () => void] {    
    if (this.Android === undefined) {
        //dev friendly polyfill

        let canceller = function() {
            console?.log("dully noting attempt to cancel scan QR request");
        };
        
        return [
            new Promise(function (resolve,reject) {
                let result = window.prompt("[cancellable] scan QR:");

                if (result == null) {
                    return reject("user cancelled window.prompt()");
                }

                return resolve(result);
            }),
            canceller];        
    }

    let promiseId = (this.nextPromiseId++).toString();
    let self = this;

    return [
        new Promise(function (resolve,reject) {
            //calls android host    
            self.promiseResolvedCallBacks.set(promiseId, (x:string) => resolve(x));
            self.promiseRejectedCallBacks.set(promiseId, (x:string) => reject(x));
            
            console?.log("calling self.Android.requestScanQr");

            self.Android.requestScanQr(promiseId, false, JSON.stringify(layoutData));
        }),
        () => {
            console?.log("requesting scanQr cancellation");
            self.Android.cancelScanQr(promiseId)
        }];
}

Window.prototype.scanQrValidatableAndCancellable = function (layoutData : contracts.LayoutStrategy, validate : ((barcode:string|null) => Promise<boolean>), onCancellation : () => void) : (() => void) {
    if (this.Android === undefined) {
        //dev friendly polyfill

        let ended = false;
        
        let canceller = function() {
            console?.log("dully noting attempt to cancel scan QR request");
            onCancellation();
            ended = true;
        };

        this.window.setTimeout(async () => {
            while (!ended) {
                while(!ended) {
                    let result = window.prompt("[cancellable] scan QR:");

                    if (result == null) {
                        console?.log("user cancelled window.prompt()");                        
                    }
        
                    let accepted = await validate(result);
                    console?.log("validator accepted?="+accepted);

                    if (accepted) {
                        canceller();
                        ended = true;
                    }
                }
            }
        }, 500);

        return canceller;
    }

    let promiseId = (this.nextPromiseId++).toString();
    window.promiseDisableAutoClean(promiseId);
    let self = this;

    let onGotBarcode = async (isCancellation:boolean, barcode:string|null) => {
        console?.log("onGotBarcode(isCancellation="+isCancellation+" barcode="+barcode+")");
        
        if (isCancellation) {
            window.promiseClean(promiseId);
            onCancellation();
        } else {
            let accepted = await validate(barcode);

            console?.log("onGotBarcode accepted?="+accepted);

            if (accepted) {
                console?.log("onGotBarcode cancellation");        
                self.Android.cancelScanQr(promiseId)
            } else {
                console?.log("onGotBarcode resumption");
                self.Android.resumeScanQr(promiseId)
            }
        }
    }

    //calls android host    
    self.promiseResolvedCallBacks.set(promiseId, (x:string) => onGotBarcode(false, x));
    self.promiseRejectedCallBacks.set(promiseId, (x:string) => onGotBarcode(true, x));

    console?.log("calling self.Android.requestScanQr");

    self.Android.requestScanQr(promiseId, true, JSON.stringify(layoutData));

    return () => {
        console?.log("requesting scanQr cancellation");        
        self.Android.cancelScanQr(promiseId)
    };
}

Window.prototype.showToast = function(label : string, longDuration : boolean) : void {
    if (this.Android === undefined) {
        //dev friendly polyfill
        window.alert(label);
        return;
    }

    this.Android.showToast(label, longDuration);
}

Window.prototype.setTitle = function (title : string) {
    document.title = title;

    if (this.Android !== undefined) {
        this.Android.setTitle(title);
    }
}

Window.prototype.setToolbarBackButtonState = function (isEnabled : boolean) {
    if (this.Android !== undefined) {
        this.Android.setToolbarBackButtonState(isEnabled);
        return;
    }

    console?.log("back button is now " + (isEnabled ? "enabled" : "disabled"));
}

Window.prototype.setScanSuccessSound = function (scanSuccessSoundUrl) {
    if (this.Android === undefined) {
        console?.log("setPausedScanOverlayImage ignoring because doesn't run within android");
        return;
    }

    let scanSuccessSoundReq = new XMLHttpRequest();       
    scanSuccessSoundReq.open("GET", scanSuccessSoundUrl, true);
    scanSuccessSoundReq.responseType = "arraybuffer";

    scanSuccessSoundReq.onload = (_) => {
        let arrayBuffer : ArrayBuffer|null = scanSuccessSoundReq.response;

        if (arrayBuffer == null) {
            console?.log("got empty arrayBuffer");
            return;
        }

        let fileContent = new Uint8Array(arrayBuffer).toString();
        window.Android.setScanSuccessSound(fileContent);
    };
    scanSuccessSoundReq.send(null);
}

Window.prototype.setPausedScanOverlayImage = function (pausedScanOverlayImageUrl) {
    if (this.Android === undefined) {
        console?.log("setPausedScanOverlayImage ignoring because doesn't run within android");
        return;
    }

    let isValidatingImageReq = new XMLHttpRequest();       
    isValidatingImageReq.open("GET", pausedScanOverlayImageUrl, true);
    isValidatingImageReq.responseType = "arraybuffer";

    isValidatingImageReq.onload = (_) => {
        let arrayBuffer : ArrayBuffer|null = isValidatingImageReq.response;

        if (arrayBuffer == null) {
            console?.log("got empty arrayBuffer");
            return;
        }

        let fileContent = new Uint8Array(arrayBuffer).toString();
        window.Android.setPausedScanOverlayImage(fileContent);
    };
    isValidatingImageReq.send(null);
}

Window.prototype.openInBrowser = function (url : string) {
    if (this.Android === undefined) {
        window.open(url, '_blank')
        return;
    }

    this.Android.openInBrowser(url);
};
Window.prototype.getKnownConnections = function() {
    if (this.Android === undefined) {        
        return "[{\"url\":\"http://wikipedia.com\",\"name\":\"Wikipedia (EN)\"}, {\"url\":\"http://duck.com\",\"name\":\"DuckDuckGo\"}]";
    }

    return this.Android.getKnownConnections();
};
Window.prototype.saveConnection = function(connInfoJson) {
    if (this.Android === undefined) {        
        return "true";
    }

    return this.Android.saveConnection(connInfoJson);
};
Window.prototype.createShortcut = function (url : string) {
    if (this.Android === undefined) {        
        return "false";
    }

    return this.Android.createShortcut(url);
};
Window.prototype.finishConnectionManager = function (url : string | null) {
    if (this.Android === undefined) {        
        return "false";
    }

    return this.Android.finishConnectionManager(url);
};

window.addEventListener('load', (_) => {
    //initialize
    window.nextPromiseId = 1;
    window.promiseNoAutoClean = new Set<string>();
    window.promiseResolvedCallBacks = new Map<string, (result:string) => void >();
    window.promiseRejectedCallBacks = new Map<string, (result:string) => void >();
});
