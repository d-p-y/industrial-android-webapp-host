
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

Window.prototype.scanQr = function(label : string, regexpOrNull : string) : Promise<string> {
    let self = this;

    return new Promise(function (resolve,reject) {        
        if (self.Android === undefined) {
            //dev friendly polyfill
            while (true) {
                let result = window.prompt(label);
    
                if (result == null) {
                    return reject("user cancelled window.prompt()");
                }
    
                if (regexpOrNull == null || RegExp(regexpOrNull).test(result)) {
                    return resolve(result);
                }
            }
        }

        //calls android host
        let promiseId = (self.nextPromiseId++).toString();
        
        self.promiseResolvedCallBacks.set(promiseId, (x:string) => resolve(x));
        self.promiseRejectedCallBacks.set(promiseId, (x:string) => reject(x));
        
        window.debugLogToBody("calling self.Android.requestScanQr");

        self.Android.requestScanQr(promiseId, label, regexpOrNull);
    });    
}

Window.prototype.showToast = function(label : string, longDuration : boolean) : void {
    if (self.Android === undefined) {
        //dev friendly polyfill
        window.alert(label);
        return;
    }

    self.Android.showToast(label, longDuration);
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

    let btnRequestScanQr = document.createElement("input");
    btnRequestScanQr.type = "button";
    btnRequestScanQr.value = "Request scan QR";
    btnRequestScanQr.onclick = async _ => {
        try {            
            let res = await window.scanQr("give me some integer QR", "^[0-9]{1,10}$");
            window.debugLogToBody("scanned: "+res);
        } catch (error) {
            window.debugLogToBody("scanner rejected: "+error);
        }
    };
    document.body.appendChild(btnRequestScanQr);
    
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

    let lbl = document.createElement("div");
    lbl.innerText = new Date().toJSON() + "";
    document.body.appendChild(lbl);    
});
