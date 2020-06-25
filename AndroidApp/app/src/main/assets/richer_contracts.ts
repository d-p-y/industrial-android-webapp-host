//
// This file contains declarations of friendly wrappers around raw contracts. They strive to provide desktop browser compatible fallbacks (where applicable)
//

///<reference path='contracts.ts'/>

class ConnectionInfo {
    public persisted : boolean = false;

    public url : string = "";
    public name : string = "";
    public webAppPersistentState : string|null = null;
    public forceReloadFromNet : boolean = false;
    public remoteDebuggerEnabled : boolean = false;
    public forwardConsoleLogToLogCat : boolean = false;
    public hapticFeedbackOnBarcodeRecognized : boolean = false;
    public mayManageConnections : boolean = false;
    public isConnectionManager : boolean = false;
    public hapticFeedbackOnAutoFocused : boolean = false;
    public hasPermissionToTakePhoto : boolean = false;
    public photoJpegQuality : number = 80; //reasonable default

    public static fromUrl(url : string) {
        let res = new ConnectionInfo();
        res.url = url;
        return res;
    }
}

interface Window {
    scanQr(layoutData : contracts.LayoutStrategy) : Promise<string>;
    scanQrCancellable(layoutData : contracts.LayoutStrategy) : [Promise<string>,() => void];

    /**          
     * @param onCancellation callback invoked when android actually performs cancellation
     * @returns function requesting android to cancel scanning
     */
    scanQrValidatableAndCancellable(layoutData : contracts.LayoutStrategy, validate : ((barcode:string|null) => Promise<boolean>), onCancellation : () => void ) : (() => void);
    
    registerMediaAsset(fromUrl:string) : Promise<string>;
    setScanSuccessSound(assetId:string) : boolean;
    setPausedScanOverlayImage(assetId:string) : boolean;
    
    showToast(label : string, longDuration : boolean) : void;    
    setToolbarBackButtonState(isEnabled : boolean) : void;
    setToolbarSearchState(activate : boolean) : void;
    setToolbarColors(backgroundColor : string, foregroundColor : string) : void;
    setToolbarItems(menuItems : contracts.MenuItemInfo[]): void;

    openInBrowser(url : string) : void;

    //helpers to keep track of promises
    nextPromiseId : number;

    promiseNoAutoClean : Set<string>;
    promiseResolvedCallBacks : Map<string, (result:string) => void>;
    promiseRejectedCallBacks : Map<string, (error:string) => void>;
    promiseDisableAutoClean(promiseId : string) : void;
    promiseClean(promiseId : string) : void;
    
    /**
     * private API returning fake mocked data when running outside of WebView
     */
    getKnownConnections() : ConnectionInfo[]; 

    /**
     * private API 
     * @returns 'true' when running outside of WebView
     */
    saveConnection(maybeExistingUrl : string|null, connInfoAsJson : ConnectionInfo) : string;

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
