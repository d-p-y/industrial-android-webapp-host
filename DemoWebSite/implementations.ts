
Window.prototype.androidPostReplyToPromise = function (replyToJson : string) {
    window.debugLogToBody("androidPostReplyToPromise("+replyToJson+")");

    let decoded : AndroidReply = JSON.parse(replyToJson);
    window.debugLogToBody("androidPostReplyToPromise decoded="+decoded);

    let noAutoClean = window.promiseNoAutoClean.has(decoded.PromiseId);

    let resolved = window.promiseResolvedCallBacks.get(decoded.PromiseId);
    
    if (resolved === undefined) {
        window.debugLogToBody("androidPostReplyToPromise resolved is undefined");
        return;
    }

    let rejected = window.promiseRejectedCallBacks.get(decoded.PromiseId);
   
    if (rejected === undefined) {
        window.debugLogToBody("androidPostReplyToPromise rejected is undefined");
        return;
    }
    
    window.debugLogToBody("androidPostReplyToPromise IsCanc="+decoded.IsCancellation+" noAutoClean="+noAutoClean + " barcode="+decoded.Barcode);
    
    if (!noAutoClean) {
        window.promiseResolvedCallBacks.delete(decoded.PromiseId);
        window.promiseRejectedCallBacks.delete(decoded.PromiseId);
    }

    if (decoded.IsCancellation === false) {
        window.debugLogToBody("androidPostReplyToPromise not cancel");
        resolved(decoded.Barcode);
        return;
    }

    if (decoded.IsCancellation === true) {
        window.debugLogToBody("androidPostReplyToPromise cancel");
        rejected(decoded.Barcode);
        return;
    }

    window.debugLogToBody("androidPostReplyToPromise unknown");
};

Window.prototype.promiseDisableAutoClean = function (promiseId : string) {
    window.promiseNoAutoClean.add(promiseId);
}

Window.prototype.promiseClean = function (promiseId : string) {
    window.promiseNoAutoClean.delete(promiseId);
    window.promiseResolvedCallBacks.delete(promiseId);
    window.promiseRejectedCallBacks.delete(promiseId);
}

Window.prototype.debugLogToBody = function (msg : string) {
    let logItm = document.createElement("div");
    logItm.innerText = msg;

    document.body.appendChild(logItm);
};

Window.prototype.scanQr = function(layoutData : LayoutStrategy) : Promise<string> {
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
        
        window.debugLogToBody("calling self.Android.requestScanQr");

        self.Android.requestScanQr(promiseId, false, JSON.stringify(layoutData));
    });    
}

Window.prototype.scanQrCancellable = function(layoutData : LayoutStrategy) : [Promise<string>, () => void] {    
    if (this.Android === undefined) {
        //dev friendly polyfill

        let canceller = function() {
            window.debugLogToBody("dully noting attempt to cancel scan QR request");
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
            
            window.debugLogToBody("calling self.Android.requestScanQr");

            self.Android.requestScanQr(promiseId, false, JSON.stringify(layoutData));
        }),
        () => {
            window.debugLogToBody("requesting scanQr cancellation");
            self.Android.cancelScanQr(promiseId)
        }];
}

Window.prototype.scanQrValidatableAndCancellable = function (layoutData : LayoutStrategy, validate : ((barcode:string|null) => Promise<boolean>) ) : (() => void) {
    if (this.Android === undefined) {
        //dev friendly polyfill

        let ended = false;
        
        let canceller = function() {
            window.debugLogToBody("dully noting attempt to cancel scan QR request");
            ended = true;
        };

        this.window.setTimeout(async () => {
            while (!ended) {
                while(!ended) {
                    let result = window.prompt("[cancellable] scan QR:");

                    if (result == null) {
                        window.debugLogToBody("user cancelled window.prompt()");                        
                    }
        
                    let accepted = await validate(result);
                    window.debugLogToBody("validator accepted?="+accepted);

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
        window.debugLogToBody("onGotBarcode(isCancellation="+isCancellation+" barcode="+barcode+")");
        
        if (isCancellation) {
            window.promiseClean(promiseId);
        } else {
            let accepted = await validate(barcode);

            window.debugLogToBody("onGotBarcode accepted?="+accepted);

            if (accepted) {
                window.debugLogToBody("onGotBarcode cancellation");        
                self.Android.cancelScanQr(promiseId)
            } else {
                window.debugLogToBody("onGotBarcode resumption");
                self.Android.resumeScanQr(promiseId)
            }
        }
    }

    //calls android host    
    self.promiseResolvedCallBacks.set(promiseId, (x:string) => onGotBarcode(false, x));
    self.promiseRejectedCallBacks.set(promiseId, (x:string) => onGotBarcode(true, x));

    window.debugLogToBody("calling self.Android.requestScanQr");

    self.Android.requestScanQr(promiseId, true, JSON.stringify(layoutData));

    return () => {
        window.debugLogToBody("requesting scanQr cancellation");        
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

    window.debugLogToBody("back button is now " + (isEnabled ? "enabled" : "disabled"));
}

interface HTMLElement {
    removeAllChildren() : void;
}

HTMLElement.prototype.removeAllChildren = function() : void {
    while (this.children.length > 0) {
        this.removeChild(this.children[0]);
    }    
}

window.addEventListener('load', (_) => {
    //initialize
    window.nextPromiseId = 1;
    window.promiseNoAutoClean = new Set<string>();
    window.promiseResolvedCallBacks = new Map<string, (result:string) => void >();
    window.promiseRejectedCallBacks = new Map<string, (result:string) => void >();
});

window.addEventListener('load', (_) => {
    document.body.removeAllChildren();
    document.body.style.backgroundColor = "#e6ffff";
    document.body.style.display = "flex";
    document.body.style.flexDirection = "column";

    {
        let container = document.createElement("div");

        let checkbox = document.createElement("input");
        checkbox.type = "checkbox";
        checkbox.id = "chbk1";
        checkbox.checked = true;
        container.appendChild(checkbox);
        
        let lbl = document.createElement("label");
        lbl.htmlFor = checkbox.id;
        lbl.textContent = "Consume backbutton event?"
        container.appendChild( lbl);
        
        document.body.appendChild(container);
        
        Window.prototype.androidBackConsumed = function () {
            window.debugLogToBody("got consume back button request");
            return checkbox.checked;
        };    
    }


    {
        let container = document.createElement("div");

        let checkbox = document.createElement("input");
        checkbox.type = "checkbox";
        checkbox.id = "chbk2";
        checkbox.checked = false;
        container.appendChild(checkbox);
        
        let lbl = document.createElement("label");
        lbl.htmlFor = checkbox.id;
        lbl.textContent = "backbutton enabled?"
        container.appendChild( lbl);
        
        document.body.appendChild(container);
        
        checkbox.addEventListener("change", _ => {
            window.debugLogToBody("back button enabled="+checkbox.checked);
            window.setToolbarBackButtonState(checkbox.checked);
        });
    }

    {
        let btnReqScan = document.createElement("input");
        btnReqScan.type = "button";
        btnReqScan.value = "Scan QR fixed height+validate+cancel";
        document.body.appendChild(btnReqScan);
        
        let divCode =  document.createElement("div")
        divCode.textContent = "<barcode>";

        divCode.style.position = "absolute";
        divCode.style.bottom = "50px";
        divCode.style.left = "50%";
        divCode.style.display = "none";
        document.body.appendChild(divCode);

        let btnAccept =  document.createElement("input")
        btnAccept.type = "button";
        btnAccept.value = "Accept";

        btnAccept.style.position = "absolute";
        btnAccept.style.bottom = "0";
        btnAccept.style.left = "0";
        btnAccept.style.display = "none";
        document.body.appendChild(btnAccept);
        

        let btnReject =  document.createElement("input")
        btnReject.type = "button";
        btnReject.value = "Reject";

        btnReject.style.position = "absolute";
        btnReject.style.bottom = "0";
        btnReject.style.left = "50%";
        btnReject.style.display = "none";
        document.body.appendChild(btnReject);
        

        let btnCancel =  document.createElement("input")
        btnCancel.type = "button";
        btnCancel.value = "Cancel scan";

        btnCancel.style.position = "absolute";
        btnCancel.style.bottom = "0";
        btnCancel.style.right = "0";
        btnCancel.style.display = "none";
        document.body.appendChild(btnCancel);


        btnReqScan.onclick = _ => {
            divCode.style.display = "initial";
            btnAccept.style.display = "initial";
            btnReject.style.display = "initial";
            btnCancel.style.display = "initial";

            let strat = new MatchWidthWithFixedHeightLayoutStrategy();
            strat.paddingTopMm = 10;
            strat.heightMm = 50;
            
            let onAccept = (_:boolean) => {};
            
            let cancellator = window.scanQrValidatableAndCancellable(
                strat, 
                async (barcode) => new Promise<boolean>(function (resolve,_) {
                    window.debugLogToBody("validation promise invoked barcode="+barcode);
                    divCode.textContent = barcode;
                    onAccept = resolve;
                }));    
        
            btnAccept.onclick = _ => {
                window.debugLogToBody("btnAccept clicked (finish)");
                onAccept(true);

                divCode.style.display = "none";            
                btnAccept.style.display = "none";
                btnReject.style.display = "none";
                btnCancel.style.display = "none";
            };

            btnReject.onclick = _ => {
                window.debugLogToBody("btnReject clicked (resume)");
                onAccept(false);
            };
    
            btnCancel.onclick = _ => {
                window.debugLogToBody("btnCancel clicked");
                cancellator();

                divCode.style.display = "none";            
                btnAccept.style.display = "none";
                btnReject.style.display = "none";
                btnCancel.style.display = "none";
            };
        };        
    }

    {
        let btnReqScan = document.createElement("input");
        btnReqScan.type = "button";
        btnReqScan.value = "Scan QR fixed height+cancel";
        document.body.appendChild(btnReqScan);
        let btnCancel =  document.createElement("input")
        btnCancel.type = "button";
        btnCancel.value = "Cancel scan";

        btnCancel.style.position = "absolute";
        btnCancel.style.bottom = "0";
        btnCancel.style.right = "0";
        btnCancel.style.display = "none";
        document.body.appendChild(btnCancel);

        btnReqScan.onclick = async _ => {
            try {
                btnCancel.style.display = "initial";
                let strat = new MatchWidthWithFixedHeightLayoutStrategy();
                strat.paddingTopMm = 10;
                strat.heightMm = 50;
                let [resultPromise, cancellator] = window.scanQrCancellable(strat);

                btnCancel.onclick = _ => {
                    window.debugLogToBody("btnCancel clicked");
                    cancellator();
                };

                let res = await resultPromise;
                window.debugLogToBody("scanned: "+res);                
            } catch (error) {
                window.debugLogToBody("scanner rejected: "+error);
            }
            btnCancel.style.display = "none";
        };        
    }

    {
        let btn = document.createElement("input");
        btn.type = "button";
        btn.value = "Scan QR with fit screen";
        btn.onclick = async _ => {
            try {            
                let strat = new FitScreenLayoutStrategy();  
                strat.screenTitle = "Need QR code";              
                let res = await window.scanQr(strat);
                window.debugLogToBody("scanned: "+res);
            } catch (error) {
                window.debugLogToBody("scanner rejected: "+error);
            }
        };
        document.body.appendChild(btn);
    }


    let btnShowToastShort = document.createElement("input");
    btnShowToastShort.type = "button";
    btnShowToastShort.value = "Short toast";
    btnShowToastShort.onclick = () => window.showToast("some short toast from web", false);    
    document.body.appendChild(btnShowToastShort);

    let btnShowToastLong = document.createElement("input");
    btnShowToastLong.type = "button";
    btnShowToastLong.value = "Long toast";
    btnShowToastLong.onclick = () => window.showToast("some long toast from web", true);    
    document.body.appendChild(btnShowToastLong);

    let btnChangeTitle = document.createElement("input");
    btnChangeTitle.type = "button";
    btnChangeTitle.value = "Change title";
    btnChangeTitle.onclick = () => window.setTitle("some title #" + new Date().getMilliseconds());
    document.body.appendChild(btnChangeTitle);

    let lbl = document.createElement("div");
    lbl.innerText = new Date().toJSON() + "";
    document.body.appendChild(lbl);

    window.setTitle("Showcase app");
});
