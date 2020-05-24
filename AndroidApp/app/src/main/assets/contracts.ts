//
// This file contains raw contracts between Android and webapp hosted in WebView
//

module contracts {
    export class LayoutStrategy {
        public typeName : string;
    
        protected constructor(typeName : string) {
            this.typeName = typeName;
        }
    }
    
    export class FitScreenLayoutStrategy extends LayoutStrategy {
        public screenTitle?:string; //optional
        public constructor() {
            super("FitScreenLayoutStrategy");
        }
    }
    
    export class MatchWidthWithFixedHeightLayoutStrategy extends LayoutStrategy {
        public paddingTopMm:number = 0; //Int
        public heightMm:number = 0; //Int
    
        public constructor() {
            super("MatchWidthWithFixedHeightLayoutStrategy");
        }
    }    
}

interface AndroidReply {
    PromiseId : string;    
    IsCancellation : boolean;
    Barcode : string;
}

interface IAndroid {
    //implicit API: triggering browser's file picker assumes intent to take photo

    /**
     * @param promiseId 
     * @param askJsForValidation when true it means that when barcode is detected scanner gets paused and waits until cancelScanQr(promisedId) or resumeScanQr(promisedId) is invoked
     * @param LayoutStrategyAsJson 
     */
    requestScanQr(promiseId : string, askJsForValidation : boolean, LayoutStrategyAsJson : string) : void;

    /**
     * cancels requested scan OR paused scan. Causes requestScanQr to be invoked with IsCancellation=true when ready
     * @param promiseId 
     */
    cancelScanQr(promiseId : string) : void;

    /**
     * resumes paused validable request. Validable is the one requested using requestScanQr(askJsForValidation=true)
     */
    resumeScanQr(promiseId : string) : void;

    /**
     * set scan success sound
     * @param fileContent comma separated ints that are valid unsigned bytes
     */
    setScanSuccessSound(fileContent : string) : void;

    /**
     * set image drawn on top of camera scanner preview when scanning is paused
     * @param fileContent comma separated ints that are valid unsigned bytes
     */
    setPausedScanOverlayImage(fileContent : string) : void;

    showToast(label : string, longDuration : boolean) : void;
    setTitle(title : string) : void;
    setToolbarBackButtonState(isEnabled : boolean) : void;
    openInBrowser(url : string) : void;

    /**
     * private, sensitive APIs that may only be called if has adequate ConnectionInfo permission
     * @returns ConnectionInfo[] as JSON. Empty if have no permission
     */
    getKnownConnections() : string;

    /**
     * private, sensitive APIs that may only be called if has adequate ConnectionInfo permission
     * @returns 'true' if success, 'false' if error or no permission
     */
    saveConnection(connInfoAsJson : string) : string;

    /**
     * private API 
     * @param url of existing registered connection as returned from getKnownConnections()
     * @returns 'true' if android didn't refuse request
     */
    createShortcut(url : string) : string;

    /**
     * private API
     * @param maybe url to navigate to
     * @returns irrelevant
     */
    finishConnectionManager(url : string | null) : string;
}

interface Window {  
    Android:IAndroid;

    androidPostReplyToPromise(replyJson : string) : void;

    /**
     * webapp is notified that backbutton was pressed and gets chance to act on it
     * @returns When 'true' webapp consumed event and it should not be processed further. When 'false' back button should be processed by parent activity (most likely causing exiting from android app)
     */
    androidBackConsumed() : boolean;     
}    
