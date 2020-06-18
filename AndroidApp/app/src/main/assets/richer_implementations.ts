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

Window.prototype.androidPostScanQrReply = function (replyToJsonUriEncoded : string) {
    console?.log("androidPostReplyToPromise("+replyToJsonUriEncoded+")");

    let decoded : IAWAppScanReply = JSON.parse(window.decodeURIComponent(replyToJsonUriEncoded));
    console?.log("androidPostReplyToPromise decoded="+decoded);

    let noAutoClean = window.promiseNoAutoClean.has(decoded.WebRequestId);

    let resolved = window.promiseResolvedCallBacks.get(decoded.WebRequestId);
    
    if (resolved === undefined) {
        console?.log("androidPostReplyToPromise resolved is undefined");
        return;
    }

    let rejected = window.promiseRejectedCallBacks.get(decoded.WebRequestId);
   
    if (rejected === undefined) {
        console?.log("androidPostReplyToPromise rejected is undefined");
        return;
    }
    
    console?.log("androidPostReplyToPromise IsCanc="+decoded.IsCancellation+" noAutoClean="+noAutoClean + " barcode="+decoded.Barcode);
    
    if (!noAutoClean) {
        window.promiseResolvedCallBacks.delete(decoded.WebRequestId);
        window.promiseRejectedCallBacks.delete(decoded.WebRequestId);
    }

    if (decoded.IsCancellation === false) {
        console?.log("androidPostReplyToPromise not cancel");
        resolved(decoded.Barcode);
        return;
    }

    if (decoded.IsCancellation === true) { //looks stupid I know but TypeScript's JSON.parse() doesn't guarantee any type safety
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
    let self : Window = this;

    return new Promise(function (resolve,reject) {
        if (self.IAWApp === undefined) {
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
        
        console?.log("calling self.IAWApp.requestScanQr");

        self.IAWApp.requestScanQr(promiseId, false, JSON.stringify(layoutData));
    });    
}

Window.prototype.scanQrCancellable = function(layoutData : contracts.LayoutStrategy) : [Promise<string>, () => void] {    
    if (this.IAWApp === undefined) {
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
    let self : Window = this;

    return [
        new Promise(function (resolve,reject) {
            //calls android host    
            self.promiseResolvedCallBacks.set(promiseId, (x:string) => resolve(x));
            self.promiseRejectedCallBacks.set(promiseId, (x:string) => reject(x));
            
            console?.log("calling self.IAWApp.requestScanQr");

            self.IAWApp.requestScanQr(promiseId, false, JSON.stringify(layoutData));
        }),
        () => {
            console?.log("requesting scanQr cancellation");
            self.IAWApp.cancelScanQr(promiseId)
        }];
}

Window.prototype.scanQrValidatableAndCancellable = function (layoutData : contracts.LayoutStrategy, validate : ((barcode:string|null) => Promise<boolean>), onCancellation : () => void) : (() => void) {
    if (this.IAWApp === undefined) {
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
    let self : Window = this;

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
                self.IAWApp.cancelScanQr(promiseId)
            } else {
                console?.log("onGotBarcode resumption");
                self.IAWApp.resumeScanQr(promiseId)
            }
        }
    }

    //calls android host    
    self.promiseResolvedCallBacks.set(promiseId, (x:string) => onGotBarcode(false, x));
    self.promiseRejectedCallBacks.set(promiseId, (x:string) => onGotBarcode(true, x));

    console?.log("calling self.IAWApp.requestScanQr");

    self.IAWApp.requestScanQr(promiseId, true, JSON.stringify(layoutData));

    return () => {
        console?.log("requesting scanQr cancellation");        
        self.IAWApp.cancelScanQr(promiseId)
    };
}

Window.prototype.showToast = function(label : string, longDuration : boolean) : void {
    if (this.IAWApp === undefined) {
        //dev friendly polyfill
        window.alert(label);
        return;
    }

    this.IAWApp.showToast(label, longDuration);
}

Window.prototype.setToolbarSearchState = function (activate : boolean) {
    if (this.IAWApp !== undefined) {
        this.IAWApp.setToolbarSearchState(activate);
        return;
    }

    console?.log("toolbar search state is now " + (activate ? "enabled" : "disabled"));
};

Window.prototype.setToolbarColors = function(bgColor, fgColor) {
    if (this.IAWApp !== undefined) {
        this.IAWApp.setToolbarColors(bgColor, fgColor);
        return;
    }

    console?.log("toolbar color bg=" + bgColor +" fg="+fgColor);    
}

Window.prototype.setToolbarBackButtonState = function (isEnabled : boolean) {
    if (this.IAWApp !== undefined) {
        this.IAWApp.setToolbarBackButtonState(isEnabled);
        return;
    }

    console?.log("back button is now " + (isEnabled ? "enabled" : "disabled"));
}

Window.prototype.setToolbarItems = function (menuItems : contracts.MenuItemInfo[]) {
    if (this.IAWApp !== undefined) {
        this.IAWApp.setToolbarItems(JSON.stringify(menuItems));
        return;
    }
    
    (window as any).devMenuItems = menuItems;
    console?.log("developer: registered menu items as window.devMenuItems");
};

Window.prototype.registerMediaAsset = function (fromUrl) {
    let self : Window = this;

    return new Promise<string>(function (resolve,reject) {
        if (self.IAWApp === undefined) {
            console?.log("registerMediaAsset ignoring because doesn't run within android");
            resolve("not-running-within-android")
            return;
        }

        let req = new XMLHttpRequest();       
        req.open("GET", fromUrl, true);
        req.responseType = "arraybuffer";

        req.onload = (_) => {
            let arrayBuffer : ArrayBuffer|null = req.response;

            if (arrayBuffer == null) {                
                reject("got empty arrayBuffer")
                return;
            }

            let fileContent = new Uint8Array(arrayBuffer).toString();
            resolve(
                window.IAWApp.registerMediaAsset(fileContent));
            return;
        };
        req.send(null);
    });
}

Window.prototype.setScanSuccessSound = function (mediaAssetId) {
    if (this.IAWApp === undefined) {
        console?.log("setScanSuccessSound ignoring because doesn't run within android");
        return true;
    }

    return window.IAWApp.setScanSuccessSound(mediaAssetId);
}

Window.prototype.setPausedScanOverlayImage = function (mediaAssetId) {
    if (this.IAWApp === undefined) {
        console?.log("setPausedScanOverlayImage ignoring because doesn't run within android");
        return true;
    }

    return window.IAWApp.setPausedScanOverlayImage(mediaAssetId);
}

Window.prototype.openInBrowser = function (url : string) {
    if (this.IAWApp === undefined) {
        window.open(url, '_blank')
        return;
    }

    this.IAWApp.openInBrowser(url);
};
Window.prototype.getKnownConnections = function() {
    if (this.IAWApp === undefined) {        
        return "[{\"url\":\"http://wikipedia.com\",\"name\":\"Wikipedia (EN)\"}, {\"url\":\"http://duck.com\",\"name\":\"DuckDuckGo\"}]";
    }

    return this.IAWApp.getKnownConnections();
};
Window.prototype.saveConnection = function(connInfoJson) {
    if (this.IAWApp === undefined) {        
        return "true";
    }

    return this.IAWApp.saveConnection(connInfoJson);
};
Window.prototype.createShortcut = function (url : string) {
    if (this.IAWApp === undefined) {        
        return "false";
    }

    return this.IAWApp.createShortcut(url);
};
Window.prototype.finishConnectionManager = function (url : string | null) {
    if (this.IAWApp === undefined) {        
        return "false";
    }

    return this.IAWApp.finishConnectionManager(url);
};

window.addEventListener('load', (_) => {
    //initialize
    window.nextPromiseId = 1;
    window.promiseNoAutoClean = new Set<string>();
    window.promiseResolvedCallBacks = new Map<string, (result:string) => void >();
    window.promiseRejectedCallBacks = new Map<string, (result:string) => void >();
});
