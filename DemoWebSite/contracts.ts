
interface AndroidReply {
    PromiseId : string;
    IsSuccess : boolean;
    Reply : string;
}

interface IAndroid {
    requestScanQr(promiseId : string, label : string, regexpOrNull : string) : void;
    showToast(label : string, longDuration : boolean) : void;
}

interface Window {  
    Android:IAndroid;

    androidPostReplyToPromise(replyJson : string) : void;

    scanQr(label : string, regexpOrNull : string) : Promise<string>;
    showToast(label : string, longDuration : boolean) : void;
    
    //helpers to keep track of promises
    nextPromiseId : number;
    promiseResolvedCallBacks : Map<string, (result:string) => void>;
    promiseRejectedCallBacks : Map<string, (error:string) => void>;
    
    debugLogToBody(msg : string) : void; //helper logger
}
