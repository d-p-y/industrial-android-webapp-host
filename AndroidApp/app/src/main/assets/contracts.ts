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

interface IAWAppScanReply {
    WebRequestId : string;    
    IsCancellation : boolean;
    Barcode : string|null;
}

interface IAWAppHostApi {
    //implicit API: triggering browser's file picker (f.e. by tapping on <input type='file'>) is understood as take photo request

    //implicit API: URL fragment element ( proto://site:port/path?query#fragment ) is understood as persistent state that webapp wants to be preserved between run times

    //implicit API: changing title is noticed and propagated as AppBar title

    /**
     * @param webRequestId scanning request identifier to be later used as parameter to cancelScanQr() resumeScanQr() androidPostScanQrReply()
     * @param askJsForValidation when true it means that when barcode is detected scanner gets paused and waits until cancelScanQr(promisedId) or resumeScanQr(promisedId) is invoked
     * @param LayoutStrategyAsJson 
     */
    requestScanQr(webRequestId : string, askJsForValidation : boolean, LayoutStrategyAsJson : string) : void;

    /**
     * cancels requested scan OR paused scan. Causes requestScanQr to be invoked with IsCancellation=true when ready
     * @param webRequestId used formerly in requestScanQr()
     */
    cancelScanQr(webRequestId : string) : void;

    /**
     * resumes paused validatable request. Validatable is the one requested using requestScanQr(askJsForValidation=true)
     * @param webRequestId used formerly in requestScanQr()
     */
    resumeScanQr(webRequestId : string) : void;

    /**
     * requests adding file into android cache dir. request is processed asynchronously and when it is actually ready its deterministic identifier (calculated from content bytes) 
     * is returned using androidPostMediaAssetReady() callback. That identifier may be used for setPausedScanOverlayImage(),setScanSuccessSound(), toolbar icons etc
     * 
     * @param webRequestId media upload-or-reuse identifier
     * @param fileContent comma separated ints that are valid unsigned bytes     
     */
    registerMediaAsset(webRequestId : string, fileContent : string) : void;

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
     * enables or disables AppBar search control. When search is active it reports users actions using androidPostToolbarSearchUpdate(bool, string)
     * 
     * @param activate 'true' to show, 'false' to hide
     */
    setToolbarSearchState(activate : boolean) : void;

    /**
     * changes AppBar's color and AppBar's text color
     * 
     * @param backgroundColor AppBar color in format understood by android.graphics.Color.parseColor() typically #RRGGBB
     * @param foregroundColor text color in format understood by android.graphics.Color.parseColor() typically #RRGGBB
     */
    setToolbarColors(backgroundColor : string, foregroundColor : string) : void;

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
    saveConnection(maybeExistingUrl : string|null, connInfoAsJson : string) : string;

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
    IAWApp:IAWAppHostApi;

    /**
     * called to notify app when QR scanner changed status or wants to post scanned result
     * @param replyJsonUriEncoded URI-encoded JSON-serialized IAWAppScanReply instance 
     */
    androidPostScanQrReply(scanReplyJsonUriEncoded : string) : void;

    /**
     * Android calls it when user activated AppBar item previously registered with setToolbarItems()
     * @param itemIdUriEncoded URI-encoded activated item's value of MenuItemInfo->webMenuItemId
     */
    androidPostToolbarItemActivated(itemIdUriEncoded : string) : void;

    /**
     * webapp is notified when user typed something into search (or confirmed search intent)
     * 
     * @param committed 'false' if user didn't yet confirm search
     * @param query text typed into search input URI-encoded
     */
    androidPostToolbarSearchUpdate(committed : boolean, query : string) : void;

    /**
     * webapp is notified when android stored file (or encountered problem). When success,  deterministic hash-based identifier is passed as 2nd param
     * @param webRequestIdUriEncoded webRequestId as requested via registerMediaAsset()
     * @param properMediaFileId actual media identifier to be used when requesting images, sounds
     */
    androidPostMediaAssetReady(webRequestIdUriEncoded : string, properMediaFileId : string) : void;

    /**
     * webapp is notified that backbutton was pressed and gets chance to act on it
     * @returns When 'true' webapp consumed event and it should not be processed further. When 'false' back button should be processed by parent activity (most likely causing exiting from android app)
     */
    androidBackConsumed() : boolean;
}    
