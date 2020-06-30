///<reference path='../richer_implementations.ts'/>

window.addEventListener('load', (_) => {
    let getStateAsJson = () => "";
    let persistState = () => {
        let i = window.document.location.href.indexOf('#');
        let newState = encodeURIComponent(getStateAsJson())

        //replace existing state if there's any
        window.document.location.href = ((i >= 0) ? window.document.location.href.substring(0, i) : window.document.location.href) + "#" + newState
    };

    let sampleIconMediaId = "";

    window.setTimeout(async () => {
        let overlayImg = await window.registerMediaAsset("test.png")
        let scanBeep = await window.registerMediaAsset("store_scanner_beep.mp3")
        sampleIconMediaId = await window.registerMediaAsset("sampleicon.png")

        let res1 = window.setPausedScanOverlayImage(overlayImg);
        console?.log("setPausedScanOverlayImage success?="+res1);

        let res2 = window.setScanSuccessSound(scanBeep);
        console?.log("setScanSuccessSound success?="+res2);    
    });

    let lblLog = document.createElement("div");
    lblLog.id = "visibleLogger";
    lblLog.style.whiteSpace = "pre";
    lblLog.textContent = new Date().toJSON() + "\n";
    let logMsg = (m:string) => {
        lblLog.textContent += m + "\n";
        persistState();
    };
    let clearLogMsg = () => {
        lblLog.textContent = "cleared at " + new Date().toJSON() + "\n";
        persistState();
    };

    document.body.removeAllChildren();
    document.body.style.backgroundColor = "#e6ffff";
    document.body.style.display = "flex";
    document.body.style.flexDirection = "column";

    {   //checkbox backEvent consume
        let container = document.createElement("div");

        let checkbox = document.createElement("input");
        checkbox.type = "checkbox";
        checkbox.id = "chbk1";
        checkbox.checked = true;
        container.appendChild(checkbox);
        
        let lbl = document.createElement("label");
        lbl.htmlFor = checkbox.id;
        lbl.textContent = "Consume backbutton event?"
        container.appendChild(lbl);
        
        document.body.appendChild(container);
        
        Window.prototype.androidBackConsumed = function () {
            logMsg("got consume back button request");
            return checkbox.checked;
        };    
    }

    {   //checkbox backbutton show
        let container = document.createElement("div");

        let checkbox = document.createElement("input");
        checkbox.type = "checkbox";
        checkbox.id = "chbk2";
        checkbox.checked = false;
        container.appendChild(checkbox);
        
        let lbl = document.createElement("label");
        lbl.htmlFor = checkbox.id;
        lbl.textContent = "backbutton enabled?"
        container.appendChild(lbl);
        
        document.body.appendChild(container);
        
        checkbox.addEventListener("change", _ => {
            console?.log("back button enabled="+checkbox.checked);
            window.setToolbarBackButtonState(checkbox.checked);
        });
    }

    {   //take photo
        let img = document.createElement("img");
        img.style.imageOrientation = "from-image";
        img.style.position = "absolute";
        img.style.maxWidth = "100vw";
        img.style.maxHeight = "100vh";
        img.style.margin = "auto";
        img.style.left = "0";
        img.style.top = "0";
        img.style.right = "0";
        img.style.bottom = "0";
        img.addEventListener("click", _ => img.style.display = "none")
        document.body.appendChild(img);
        
    
        let imgResponseForm = document.createElement("form");
        let imgResponse = document.createElement("input");    
        imgResponse.type = "file";
        imgResponse.style.display = "none";
        imgResponse.addEventListener("change", _ => {
            if (imgResponse.files?.length != 1) {
                return;
            }
    
            let fr = new FileReader();
            fr.onload = ev => {
                let x = ev.target?.result;
                if (typeof x !== typeof "") {
                    console?.log("file was not read as dataURL");
                    return;
                }            
                img.src = x as string;
                img.style.display = "initial";
                imgResponseForm.reset();//prepare for next requests
            };
            fr.readAsDataURL(imgResponse?.files[0])
        });
    
        imgResponseForm.appendChild(imgResponse);
        document.body.appendChild(imgResponseForm);
    
        let imgReq = document.createElement("input");
        imgReq.type = "button";
        imgReq.value = "Take photo";
        document.body.appendChild(imgReq);
    
        imgReq.addEventListener("click", () => imgResponse.click());
    }

    {
        let btnReqScan = document.createElement("input");
        btnReqScan.type = "button";
        btnReqScan.value = "Scan QR fixed height at bottom+validate+cancel";
        document.body.appendChild(btnReqScan);
        
        btnReqScan.onclick = _ => {
            let cntrlsContainer = document.createElement("div")
            cntrlsContainer.style.display = "grid";
            cntrlsContainer.style.width = "100vw";
            cntrlsContainer.style.position = "absolute";
            cntrlsContainer.style.bottom = "0";
            cntrlsContainer.style.left = "0";
            cntrlsContainer.style.backgroundColor = "white"; //to fully cover on multiple scans
            cntrlsContainer.style.setProperty("grid-template-columns", "1fr 1fr 1fr");
            
            let divCode =  document.createElement("div");
            divCode.textContent = "<barcode>";
            divCode.style.setProperty("grid-column", "1/4");
            divCode.style.setProperty("grid-row", "1");
            divCode.style.setProperty("justify-self", "center");
            divCode.style.visibility = "hidden";
            cntrlsContainer.appendChild(divCode);
            
            let btnAccept =  document.createElement("input")
            btnAccept.type = "button";
            btnAccept.value = "Accept";
            btnAccept.style.setProperty("grid-column", "1");
            btnAccept.style.setProperty("grid-row", "2");
            btnAccept.style.setProperty("margin-right", "auto"); //thanks Rachel for "The New CSS Layout"
            btnAccept.style.visibility = "hidden";
            cntrlsContainer.appendChild(btnAccept);
            
            let btnReject =  document.createElement("input")
            btnReject.type = "button";
            btnReject.value = "Reject";
            btnReject.style.setProperty("grid-column", "2");
            btnReject.style.setProperty("grid-row", "2");
            btnReject.style.setProperty("margin", "0 auto 0 auto");
            btnReject.style.visibility = "hidden";
            cntrlsContainer.appendChild(btnReject);
            
            let btnCancel =  document.createElement("input")
            btnCancel.type = "button";
            btnCancel.value = "Cancel scan";
            btnCancel.style.setProperty("grid-column", "3");
            btnCancel.style.setProperty("grid-row", "2");
            btnCancel.style.setProperty("margin-left", "auto");
            cntrlsContainer.appendChild(btnCancel);

            document.body.appendChild(cntrlsContainer);

            let strat = new contracts.MatchWidthWithFixedHeightLayoutStrategy();
            strat.paddingOriginIsTop = false;
            strat.paddingMm = 10;
            strat.heightMm = 30;
            
            let onAccept = (_:boolean) => {};
            let scanResult : string | null = null
            
            let cancellator = window.scanQrValidatableAndCancellable(
                strat, 
                async (barcode) => new Promise<boolean>(function (resolve,_) {
                    console?.log("validation promise invoked barcode="+barcode);
                    divCode.textContent = barcode;
                    onAccept = resolve;
                    btnAccept.style.visibility = "initial";
                    btnReject.style.visibility = "initial";
                    divCode.style.visibility = "initial";
                }),
                () => {
                    //android confirmed cancellation
                    document.body.removeChild(cntrlsContainer);
                    
                    logMsg((scanResult == null) ? "not scanned" : ("scanned: " + scanResult));
                });    
        
            btnAccept.onclick = _ => {
                console?.log("btnAccept clicked (finish)");
                scanResult = divCode.textContent
                onAccept(true);
            };

            btnReject.onclick = _ => {
                console?.log("btnReject clicked (resume)");
                scanResult = null
                onAccept(false);                
                btnAccept.style.visibility = "hidden";
                btnReject.style.visibility = "hidden";
                divCode.style.visibility = "hidden";
            };
    
            btnCancel.onclick = _ => {
                console?.log("btnCancel clicked");
                cancellator();                
            };
        };        
    }

    {
        let btnReqScan = document.createElement("input");
        btnReqScan.type = "button";
        btnReqScan.value = "Scan QR fixed height+cancel";
        document.body.appendChild(btnReqScan);
        
        btnReqScan.onclick = async _ => {
            let btnCancel =  document.createElement("input")
            btnCancel.type = "button";
            btnCancel.value = "Cancel scan";
            btnCancel.style.position = "absolute";
            btnCancel.style.bottom = "0";
            btnCancel.style.right = "0";        
            document.body.appendChild(btnCancel);
            
            try {
                btnCancel.style.display = "initial";
                let strat = new contracts.MatchWidthWithFixedHeightLayoutStrategy();
                strat.paddingMm = 10;
                strat.heightMm = 50;
                let [resultPromise, cancellator] = window.scanQrCancellable(strat);

                btnCancel.onclick = _ => {
                    console?.log("btnCancel clicked");
                    cancellator();
                };

                let res = await resultPromise;
                console?.log("scanned: "+res);
                logMsg("scanned " + res);
            } catch (error) {
                console?.log("scanner rejected: "+error);
                logMsg("not scanned");
            }
            document.body.removeChild(btnCancel);
        };        
    }

    {
        let btn = document.createElement("input");
        btn.type = "button";
        btn.value = "Scan QR with fit screen";
        btn.onclick = async _ => {
            try {            
                let strat = new contracts.FillScreenLayoutStrategy();  
                strat.screenTitle = "Need QR code";              
                let res = await window.scanQr(strat);
                console?.log("scanned: "+res);
                logMsg("scanned " + res);
            } catch (error) {
                console?.log("scanner rejected: "+error);
                logMsg("not scanned");
            }
        };
        document.body.appendChild(btn);
    }

    {
        let added = false;
        let getBtnCaption = () => added ? "Remove menu items" : "Add menu items";
        let addRemoveMenuItem = document.createElement("input");
        addRemoveMenuItem.type = "button";
        addRemoveMenuItem.value = getBtnCaption();
        addRemoveMenuItem.onclick = () => {
            if (added) {                
                window.setToolbarItems([]);
            } else {
                let itm1 = new contracts.MenuItemInfo();
                itm1.trueForAction = true;
                itm1.title = "Demo";
                itm1.iconMediaIdentifierId = sampleIconMediaId;
                itm1.webMenuItemId = "demoActionId";

                let itm2 = new contracts.MenuItemInfo();
                itm2.trueForAction = false;
                itm2.title = "Demo menu item";
                itm2.webMenuItemId = "demoItemId";

                window.setToolbarItems([itm1, itm2]);
            }

            added = !added;
            addRemoveMenuItem.value = getBtnCaption();
        };
        document.body.appendChild(addRemoveMenuItem);

        Window.prototype.androidPostToolbarItemActivated = function (webMenuItemId) {
            webMenuItemId = window.decodeURIComponent(webMenuItemId);
            
            logMsg("menu item pressed: "+webMenuItemId);
        };    
    }


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

    let btnChangeTitle = document.createElement("input");
    btnChangeTitle.type = "button";
    btnChangeTitle.value = "Change title";
    btnChangeTitle.onclick = () => {
        document.title = "some title #" + new Date().getMilliseconds();
        persistState();
    };
    document.body.appendChild(btnChangeTitle);

    {
        let fgCol = ["#ff0000","#00ff00","#0000ff","#000000"];
        let bgCol = ["#00ff00","#0000ff","#ff0000","#ffffff"];
        let iCol = 0;

        let btnChangeColors = document.createElement("input");
        btnChangeColors.type = "button";
        btnChangeColors.value = "Change AppBar colors";
        btnChangeColors.onclick = () => {
            window.setToolbarColors(bgCol[iCol], fgCol[iCol]);            
            iCol = ++iCol % fgCol.length;
        };
        document.body.appendChild(btnChangeColors);
    }

    {
        let searchEnabled = false;
        let getBtnCaption = () => searchEnabled ? "Disable search-in-appbar" : "Enable search-in-appbar";
        let toggleAppBarSearch = document.createElement("input");
        toggleAppBarSearch.type = "button";
        toggleAppBarSearch.value = getBtnCaption();
        toggleAppBarSearch.onclick = () => {
            searchEnabled = !searchEnabled;
            window.setToolbarSearchState(searchEnabled);
            toggleAppBarSearch.value = getBtnCaption();
        };
        document.body.appendChild(toggleAppBarSearch);

        Window.prototype.androidPostToolbarSearchUpdate = function (committed, query) {
            query = decodeURIComponent(query);

            logMsg((committed ? "searchRequest" : "searchTextUpdate") + ": " + query);
        };    
    }

    let btnClearLog = document.createElement("input");
    btnClearLog.type = "button";
    btnClearLog.value = "Clear log below";
    btnClearLog.onclick = () => clearLogMsg();
    document.body.appendChild(btnClearLog);

    document.body.appendChild(lblLog);
    
    getStateAsJson = () => JSON.stringify([lblLog.textContent, document.title]);
   
    let iFrag = document.location.href.indexOf("#");
    if (iFrag >= 0) {        
        let fromJson : [string, string] = JSON.parse(decodeURIComponent(document.location.href.substring(iFrag+1)))
        console?.log("got state: restoring it: "+ fromJson[0])
        lblLog.textContent = fromJson[0];
        document.title = fromJson[1];
    } else {
        document.title = "Showcase app";
    }
});        
