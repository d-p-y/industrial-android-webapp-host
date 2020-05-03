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
    IsCancellation : boolean;
    Barcode : string;
}

interface IAndroid {
    /**
     * @param promiseId 
     * @param askJsForValidation when true it means that when barcode is detected scanner gets paused and waits until cancelScanQr(promisedId) or resumeScanQr(promisedId) is invoked
     * @param LayoutStrategyAsJson 
     */
    requestScanQr(promiseId : string, askJsForValidation : boolean, LayoutStrategyAsJson : string) : void;

    /**
     * cancels requested scan OR paused scan
     * @param promiseId 
     */
    cancelScanQr(promiseId : string) : void;
    resumeScanQr(promiseId : string) : void;

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
    scanQrValidatableAndCancellable(layoutData : LayoutStrategy, validate : ((barcode:string|null) => Promise<boolean>) ) : (() => void);
    showToast(label : string, longDuration : boolean) : void;
    setTitle(title : string) : void;
    setToolbarBackButtonState(isEnabled : boolean) : void;

    //helpers to keep track of promises
    nextPromiseId : number;

    promiseNoAutoClean : Set<string>;
    promiseResolvedCallBacks : Map<string, (result:string) => void>;
    promiseRejectedCallBacks : Map<string, (error:string) => void>;
    promiseDisableAutoClean(promiseId : string) : void;
    promiseClean(promiseId : string) : void;    
}
