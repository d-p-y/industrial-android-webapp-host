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
    console?.log("androidPostScanQrReply("+replyToJsonUriEncoded+")");

    let decoded : IAWAppScanReply = JSON.parse(window.decodeURIComponent(replyToJsonUriEncoded));
    console?.log("androidPostScanQrReply decoded="+decoded);

    let resolved = window.callbackScanQr.get(decoded.WebRequestId);
    
    if (resolved === undefined) {
        console?.log("androidPostScanQrReply resolved is undefined");
        return;
    }

    console?.log("androidPostScanQrReply IsDisposal="+decoded.IsDisposal + " IsPaused"+ decoded.IsPaused +" IsCanc="+decoded.IsCancellation + " barcode="+decoded.Barcode);
    
    if (decoded.IsPaused == true) {
        console?.log("androidPostScanQrReply scanner acknowledged that it is paused");
        return
    }

    if (decoded.IsDisposal == true) {
        window.callbackScanQr.delete(decoded.WebRequestId);
        return;
    }

    console?.log("androidPostScanQrReply calling resolved()");
    resolved(decoded);
    return;
};

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
        let resolved = false;
        
        self.callbackScanQr.set(promiseId, (x:IAWAppScanReply) => {
            if (resolved) {
                console?.log("promise already resolved");
                return;
            }
            resolved = true;

            if (x.IsCancellation || x.Barcode == null) {
                reject("cancellation or null barcode");
            } else {
                resolve(x.Barcode);
            }            
        });
                
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
    let resolved = false;

    return [
        new Promise(function (resolve,reject) {
            //calls android host    
            self.callbackScanQr.set(
                promiseId, 
                (x:IAWAppScanReply) => {
                    if (resolved) {
                        console?.log("promise already resolved");
                        return;
                    }
                    resolved = true;

                    if (x.IsCancellation || x.Barcode == null) {
                        reject("cancellation or null barcode")
                    } else {
                        resolve(x.Barcode);
                    }
                });
                        
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
    let self : Window = this;

    let onGotBarcode = async (reply:IAWAppScanReply) => {
        console?.log("onGotBarcode(isDisposal=" + reply.IsDisposal + " isCancellation="+reply.IsCancellation+" barcode="+reply.Barcode+")");
        
        if (reply.IsDisposal) {
            return;
        }

        if (reply.IsCancellation) {            
            onCancellation();
        } else {
            let accepted = await validate(reply.Barcode);

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
    self.callbackScanQr.set(promiseId, (x:IAWAppScanReply) => onGotBarcode(x));
    
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

Window.prototype.androidPostMediaAssetReady = function (rawPromiseId : string, properMediaFileId : string) {
    let promiseId = window.decodeURIComponent(rawPromiseId);
    console?.log("androidPostMediaAssetReady(" + promiseId + "," + properMediaFileId +" )");
    
    let resolved = window.callbackRegisterMedia.get(promiseId);
    
    if (resolved === undefined) {
        console?.log("androidPostMediaAssetReady resolved is undefined");
        return;
    }
        
    window.callbackRegisterMedia.delete(promiseId);
    
    console?.log("androidPostMediaAssetReady calling resolved?"+ (properMediaFileId.length >0));
    
    resolved(properMediaFileId);
};

Window.prototype.registerMediaAsset = function (fromUrl) {
    let self : Window = this;

    let dl = new Promise<string>(function (resolve,reject) {
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

            console?.log("downloaded file, passing it to next promise");
            resolve(
                new Uint8Array(arrayBuffer).toString());

            return;
        };
        req.send(null);
    });

    let ul = (fileContent : string) : Promise<string> => {
        let promiseId = (this.nextPromiseId++).toString();
       
        return new Promise<string>(function (resolve, _) {                                    
            self.callbackRegisterMedia.set(promiseId, (x:string) => {
                console?.log("registerMediaAsset confirmed mediaAssetId=" + x);
                resolve(x);
            });
            
            console?.log("calling registerMediaAsset");
            window.IAWApp.registerMediaAsset(promiseId, fileContent);
        });
    };

    return dl.then(ul);
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
        let fst = new ConnectionInfo();
        fst.url = "http://wikipedia.com";
        fst.name = "Wikipedia (EN)";
        fst.photoJpegQuality = 80;

        let snd = new ConnectionInfo();
        snd.url = "http://duck.com";
        snd.name = "DuckDuckGo";
        snd.photoJpegQuality = 90;

        return [fst, snd];
    }

    let res : ConnectionInfo[] = JSON.parse(this.IAWApp.getKnownConnections());
    return res;
};
Window.prototype.saveConnection = function(maybeExistingUrl, connInfoJson) {
    if (this.IAWApp === undefined) {        
        return "true";
    }

    return this.IAWApp.saveConnection(maybeExistingUrl, JSON.stringify(connInfoJson));
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
    window.callbackScanQr = new Map<string, (result:IAWAppScanReply) => void >();
    window.callbackRegisterMedia = new Map<string, (result:string) => void >();
});
