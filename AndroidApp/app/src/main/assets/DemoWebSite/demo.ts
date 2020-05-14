///<reference path='../richer_implementations.ts'/>

window.addEventListener('load', (_) => {
    window.setPausedScanOverlayImage("test.png");

    let lblLog = document.createElement("div");
    lblLog.style.whiteSpace = "pre";
    lblLog.textContent = new Date().toJSON() + "\n";

    document.body.removeAllChildren();
    document.body.style.backgroundColor = "#e6ffff";
    document.body.style.display = "flex";
    document.body.style.flexDirection = "column";

    {
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
            lblLog.textContent += "got consume back button request\n";
            return checkbox.checked;
        };    
    }


    {
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

    {
        let btnReqScan = document.createElement("input");
        btnReqScan.type = "button";
        btnReqScan.value = "Scan QR fixed height+validate+cancel";
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
            strat.paddingTopMm = 20;
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
                    
                    lblLog.textContent += (scanResult == null) ? "not scanned\n" : ("scanned: " + scanResult + "\n");
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
                strat.paddingTopMm = 10;
                strat.heightMm = 50;
                let [resultPromise, cancellator] = window.scanQrCancellable(strat);

                btnCancel.onclick = _ => {
                    console?.log("btnCancel clicked");
                    cancellator();
                };

                let res = await resultPromise;
                console?.log("scanned: "+res);
                lblLog.textContent += "scanned " + res  +"\n";
            } catch (error) {
                console?.log("scanner rejected: "+error);
                lblLog.textContent += "not scanned\n";
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
                let strat = new contracts.FitScreenLayoutStrategy();  
                strat.screenTitle = "Need QR code";              
                let res = await window.scanQr(strat);
                console?.log("scanned: "+res);
                lblLog.textContent += "scanned " + res  +"\n";
            } catch (error) {
                console?.log("scanner rejected: "+error);
                lblLog.textContent += "not scanned\n";
            }
        };
        document.body.appendChild(btn);
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
    btnChangeTitle.onclick = () => window.setTitle("some title #" + new Date().getMilliseconds());
    document.body.appendChild(btnChangeTitle);
    
    document.body.appendChild(lblLog);

    window.setTitle("Showcase app");
});        
