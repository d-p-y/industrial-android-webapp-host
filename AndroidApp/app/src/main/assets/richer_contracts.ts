//
// This file contains declarations of friendly wrappers around raw contracts. They strive to provide desktop browser compatible fallbacks (where applicable)
//

///<reference path='contracts.ts'/>

interface Window {
    scanQr(layoutData : contracts.LayoutStrategy) : Promise<string>;
    scanQrCancellable(layoutData : contracts.LayoutStrategy) : [Promise<string>,() => void];

    /**          
     * @param onCancellation callback invoked when android actually performs cancellation
     * @returns function requesting android to cancel scanning
     */
    scanQrValidatableAndCancellable(layoutData : contracts.LayoutStrategy, validate : ((barcode:string|null) => Promise<boolean>), onCancellation : () => void ) : (() => void);
    
    setScanSuccessSound(fromUrl:string) : void;
    setPausedScanOverlayImage(fromUrl:string) : void;
    
    showToast(label : string, longDuration : boolean) : void;    
    setToolbarBackButtonState(isEnabled : boolean) : void;
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
    getKnownConnections() : string; 

    /**
     * private API 
     * @returns 'true' when running outside of WebView
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
