class LayoutStrategy {
    public typeName : string;

    protected constructor(typeName : string) {
        this.typeName = typeName;
    }
}

class FitScreenLayoutStrategy extends LayoutStrategy {
    public screenTitle?:string; //optional
    public constructor() {
        super("FitScreenLayoutStrategy");
    }
}

class MatchWidthWithFixedHeightLayoutStrategy extends LayoutStrategy {
    public paddingTopMm:number = 0; //Int
    public heightMm:number = 0; //Int

    public constructor() {
        super("MatchWidthWithFixedHeightLayoutStrategy");
    }
}

interface AndroidReply {
    PromiseId : string;
    IsSuccess : boolean;
    Reply : string;
}

interface IAndroid {
    requestScanQr(promiseId : string, LayoutStrategyAsJson : string) : void;
    cancelScanQr(promiseId : string) : void;
    showToast(label : string, longDuration : boolean) : void;
    setTitle(title : string) : void;
    setToolbarBackButtonState(isEnabled : boolean) : void;
}

interface Window {  
    Android:IAndroid;

    androidPostReplyToPromise(replyJson : string) : void;
    androidBackConsumed() : boolean;
    
    scanQr(layoutData : LayoutStrategy) : Promise<string>;
    scanQrCancellable(layoutData : LayoutStrategy) : [Promise<string>,() => void];
    showToast(label : string, longDuration : boolean) : void;
    setTitle(title : string) : void;
    setToolbarBackButtonState(isEnabled : boolean) : void;

    //helpers to keep track of promises
    nextPromiseId : number;
    promiseResolvedCallBacks : Map<string, (result:string) => void>;
    promiseRejectedCallBacks : Map<string, (error:string) => void>;
    
    debugLogToBody(msg : string) : void; //helper logger
}
