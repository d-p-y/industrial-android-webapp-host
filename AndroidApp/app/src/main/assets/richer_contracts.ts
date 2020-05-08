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
    setPausedScanOverlayImage(fromUrl:string) : void;

    showToast(label : string, longDuration : boolean) : void;
    setTitle(title : string) : void;
    setToolbarBackButtonState(isEnabled : boolean) : void;
    openInBrowser(url : string) : void;

    //helpers to keep track of promises
    nextPromiseId : number;

    promiseNoAutoClean : Set<string>;
    promiseResolvedCallBacks : Map<string, (result:string) => void>;
    promiseRejectedCallBacks : Map<string, (error:string) => void>;
    promiseDisableAutoClean(promiseId : string) : void;
    promiseClean(promiseId : string) : void;   
}    
