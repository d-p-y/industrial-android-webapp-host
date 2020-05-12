///<reference path='../contracts.ts'/>
///<reference path='../richer_implementations.ts'/>

window.addEventListener('load', (_) => {
    document.body.removeAllChildren();
    document.body.style.backgroundColor = "#e6ffff";
    document.body.style.display = "flex";
    document.body.style.flexDirection = "column";
    document.body.style.height = "100vh";
    
    let scannedCode = document.createElement("div");
    scannedCode.style.fontSize = "34px";
    scannedCode.style.wordWrap = "anywhere";
    scannedCode.style.display = "none";
    scannedCode.style.width = "95vw";
    scannedCode.style.alignSelf = "center";
    scannedCode.style.marginTop = "50px";
    document.body.appendChild(scannedCode);

    let actionsBar = document.createElement("div");
    actionsBar.style.display = "none";
    actionsBar.style.flexDirection = "row";
    actionsBar.style.display = "none";
    actionsBar.style.marginTop = "auto";
    actionsBar.style.marginBottom = "15px";
    actionsBar.style.justifyContent = "space-between";

    let actionBrowseIt = document.createElement("input");    
    actionBrowseIt.value = "Browse it";
    actionBrowseIt.type = "button";
    actionBrowseIt.addEventListener("click", 
        _ => window.openInBrowser(scannedCode.textContent as string));
    actionsBar.appendChild(actionBrowseIt);

    let actionInetSearchIt = document.createElement("input");    
    actionInetSearchIt.value = "Google it";
    actionInetSearchIt.type = "button";
    actionInetSearchIt.addEventListener("click", 
        _ => window.openInBrowser("https://google.com?q="+ encodeURIComponent(scannedCode.textContent as string)));
    actionsBar.appendChild(actionInetSearchIt);

    let actionDone = document.createElement("input");    
    actionDone.value = "OK done";
    actionDone.type = "button";
    actionDone.addEventListener("click", 
        _ => {
            actionsBar.style.display = "none";
            scannedCode.style.display = "none";
            btnScanQr.style.display = "initial";
        });
    actionsBar.appendChild(actionDone);
    document.body.appendChild(actionsBar);
    
    let btnScanQr = document.createElement("input");
    btnScanQr.value = "Scan QR";
    btnScanQr.type = "button";
    btnScanQr.style.alignSelf = "center";
    btnScanQr.style.marginTop = "auto";
    btnScanQr.style.marginBottom = "15px";
    
    btnScanQr.addEventListener("click", async _ => {
        btnScanQr.style.display = "none";
        try {
            var scannedContent = await window.scanQr(new contracts.FitScreenLayoutStrategy());
            scannedCode.textContent = scannedContent;
            
            actionBrowseIt.style.display = 
                (scannedContent.toLowerCase().startsWith("http://") || scannedContent.toLowerCase().startsWith("https://") ) ? "initial" : "none";
            
            scannedCode.style.display = "initial";
            actionsBar.style.display = "flex";
        } catch(ex) {
            btnScanQr.style.display = "initial";
        }    
    } );
    document.body.appendChild(btnScanQr);

    window.setTitle("Simple QR scanner");
});        