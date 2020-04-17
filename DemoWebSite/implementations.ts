
Window.prototype.androidPostReplyToPromise = function (replyToJson : string) {
    window.debugLogToBody("processing android reply: "+replyToJson);

    let decoded : AndroidReply = JSON.parse(replyToJson);
    window.debugLogToBody("processing android reply[2]: "+decoded);

    let resolved = window.promiseResolvedCallBacks.get(decoded.PromiseId);
    window.promiseResolvedCallBacks.delete(decoded.PromiseId);
    if (resolved === undefined) {
        window.debugLogToBody("resolved reply is undefined");
        return;
    }

    let rejected = window.promiseRejectedCallBacks.get(decoded.PromiseId);
    window.promiseRejectedCallBacks.delete(decoded.PromiseId);
    if (rejected === undefined) {
        window.debugLogToBody("rejected reply is undefined");
        return;
    }
    
    window.debugLogToBody("IsSuccess:"+decoded.IsSuccess);

    if (decoded.IsSuccess === true) {
        window.debugLogToBody("success reply " + decoded.Reply);
        resolved(decoded.Reply);
        return;
    }

    if (decoded.IsSuccess === false) {
        window.debugLogToBody("failure reply " + decoded.Reply);
        rejected(decoded.Reply);
        return;
    }

    window.debugLogToBody("got promise reply that is neither success nor failure");
};

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

        self.Android.requestScanQr(promiseId, JSON.stringify(layoutData));
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

            self.Android.requestScanQr(promiseId, JSON.stringify(layoutData));
        }),
        () => {
            window.debugLogToBody("requesting scanQr cancellation");
            self.Android.cancelScanQr(promiseId)
        }];
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
        
        checkbox.addEventListener("change", ev => {
            window.debugLogToBody("back button enabled="+checkbox.checked);
            window.setToolbarBackButtonState(checkbox.checked);
        });
    }

    {
        let btnReqScan = document.createElement("input");
        btnReqScan.type = "button";
        btnReqScan.value = "Scan QR with fixed height";        
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

                btnCancel.onclick = async _ => {
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
