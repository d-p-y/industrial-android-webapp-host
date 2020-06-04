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
    
    /**
     * trueForAction: 'true' means it's an action outside of "three dots menu". 'false' means menuitem is present within "three dots menu"
     */
    export class MenuItemInfo {
        public webMenuItemId : string = "";
        public trueForAction : boolean = false;
        public title : string = "None";
        public enabled : boolean = true;
        public iconMediaIdentifierId : string|null = null;
    }
}

interface AndroidReply {
    PromiseId : string;    
    IsCancellation : boolean;
    Barcode : string;
}

interface IAndroid {
    //implicit API: triggering browser's file picker (f.e. by tapping on <input type='file'>) is understood as take photo request

    //implicit API: URL fragment element ( proto://site:port/path?query#fragment ) is understood as persistent state that webapp wants to be preserved between run times

    //implicit API: changing title is noticed and propagated as AppBar title

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
     * adds file into android cache dir and returns its identifier (to be used for setPausedScanOverlayImage(),setScanSuccessSound(), toolbar icons etc ).
     * NOTE: returned mediaAssetHandleId is not ready immediately as bytes are stored to disk in parallel!
     * 
     * @param fileContent comma separated ints that are valid unsigned bytes
     * @returns mediaAssetHandleId
     */
    registerMediaAsset(fileContent : string) : string;

    /**
     * set scan success sound
     * @param as returned by registerMediaAsset() call
     * @returns true if asset is known (is still in cache)
     */
    setScanSuccessSound(mediaAssetIdentifier : string) : boolean;

    /**
     * set image drawn on top of camera scanner preview when scanning is paused
     * @param as returned by registerMediaAsset() call
     * @returns true if asset is known (is still in cache)
     */
    setPausedScanOverlayImage(mediaAssetIdentifier : string) : boolean;

    showToast(label : string, longDuration : boolean) : void;    
    setToolbarBackButtonState(isEnabled : boolean) : void;
    openInBrowser(url : string) : void;

    /**
     * replaces AppBar menu items with provided items. When item is activated by user androidPostToolbarItemActivated() will be called
     * 
     * @param menuItemInfosAsJson JSONized list of MenuItemInfo instances
     */
    setToolbarItems(menuItemInfosAsJson : string): void;


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

    /**
     * called to notify app when QR scanner changed status or wants to post scanned result
     * @param replyJsonUriEncoded URI-encoded JSONized AndroidReply instance 
     */
    androidPostReplyToPromise(replyJsonUriEncoded : string) : void;

    /**
     * Android calls it when user activated AppBar item previously registered with setToolbarItems()
     * @param itemIdUriEncoded URI-encoded activated item's value of MenuItemInfo->webMenuItemId
     */
    androidPostToolbarItemActivated(itemIdUriEncoded : string) : void;

    /**
     * webapp is notified that backbutton was pressed and gets chance to act on it
     * @returns When 'true' webapp consumed event and it should not be processed further. When 'false' back button should be processed by parent activity (most likely causing exiting from android app)
     */
    androidBackConsumed() : boolean;
}    
