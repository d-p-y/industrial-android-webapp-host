///<reference path='../contracts.ts'/>
///<reference path='../richer_implementations.ts'/>

function createShortcutForm(
        title : string,
        action : ((_:ConnectionInfo, onFinished:(() => void) ) => void),
        onBack : (() => void)) : Node {

    document.title = title;
    window.setToolbarBackButtonState(true);
    window.androidBackConsumed = () => {
        onBack();
        return true
    };
    
    let rawConnections = window.getKnownConnections();

    let result = document.createElement("div");
    result.style.display = "flex";
    result.style.flexDirection = "column";
    
    let btns = 
        rawConnections
            .filter(x => x.name != null && x.url != null)
            .map(x => {
                let btn = document.createElement("input");
                btn.type = "button";
                btn.addEventListener("click", _ => {
                    action(x, onBack);
                });
                btn.value = x.name;
                return btn;
            });
                       
    result.appendChildren(btns);

    return result;
}

function createConnectionChooserForm(
        activateNewConnectionScreen : (() => void), 
        activateChooseItemForShortcutScreen : (() => void),
        activateChooseItemToEditScreen : (() => void),
        activateChooseItemToRemoveScreen : (() => void) ) : Node {

    document.title = "Connect to";
    window.setToolbarBackButtonState(false);
    window.androidBackConsumed = () => false;

    let rawConnections = window.getKnownConnections().filter(x => !x.isConnectionManager);

    let result = document.createElement("div");
    result.className = "chooser";
    
    let btns = 
        rawConnections
            .filter(x => x.name != null && x.url != null)
            .map(x => {
                let btn = document.createElement("input");
                btn.type = "button";
                btn.addEventListener("click", _ => window.location.href = x.url);
                btn.value = x.name;
                return btn;
            });
                       
    result.appendChildren(btns);

    let actionsWrapper = document.createElement("details");

    let summary = document.createElement("summary");
    summary.textContent = "More actions...";    
    actionsWrapper.appendChild(summary);

    let details = document.createElement("div");
    actionsWrapper.appendChild(details);

    result.appendChild(actionsWrapper);

    let addItem = document.createElement("a");
    addItem.href = "#";
    addItem.textContent = "Add connection...";
    addItem.addEventListener("click", ev => {
        ev.preventDefault(); 
        console.log("switching to createConnection");
        activateNewConnectionScreen();
    });
    details.appendChild(addItem);

    let editItem = document.createElement("a");
    editItem.href = "#";
    editItem.textContent = "Edit connection...";
    editItem.addEventListener("click", ev => {
        ev.preventDefault(); 
        console.log("switching to chooseItemToEditScreen");
        activateChooseItemToEditScreen();
    });
    details.appendChild(editItem);

    let removeItem = document.createElement("a");
    removeItem.href = "#";
    removeItem.textContent = "Remove connection...";
    removeItem.addEventListener("click", ev => {
        ev.preventDefault(); 
        console.log("switching to chooseItemToRemoveScreen");
        activateChooseItemToRemoveScreen();
    });
    details.appendChild(removeItem);

    
    let createShortcut = document.createElement("a");
    createShortcut.href = "#";
    createShortcut.textContent = "Create shortcut...";
    createShortcut.addEventListener("click", ev => {
        ev.preventDefault(); 
        console.log("switching to createShortcut");
        activateChooseItemForShortcutScreen();
    });
    details.appendChild(createShortcut);


    return result;
}

function createSimpleSwitch() : [HTMLInputElement, HTMLLabelElement] {
    let res = document.createElement("label");
    res.className = "switch";

    let inp = document.createElement("input");
    inp.type = "checkbox";

    res.appendChild(inp);

    let cnt = document.createElement("div");
    cnt.className = "container";
    
    let tgl = document.createElement("div");
    tgl.className = "toggle";

    cnt.appendChild(tgl);    
    res.appendChild(cnt);

    return [inp, res];

    //<label class="switch"><input type="checkbox"><div class="container"><div class="toggle"></div></div></label>
}

function createConnectionCreatorEditorForm(
        onBack : (() => void) | null, 
        onDone : ((url:string) => void), 
        ci : ConnectionInfo) : Node {

    let ciUrl = (ci?.url == null || ci.url.length <= 0) ? null : ci.url;
    document.title = ci.persisted ? "Edit connection" : "New connection";
    window.setToolbarBackButtonState(onBack != null);
    window.androidBackConsumed = () => {
        if (onBack != null) {
            onBack();
        }
        return true
    };

    let result = document.createElement("div");
    result.className = "createOrEdit";

    let inpName = document.createElement("input");
    let inpUrl = document.createElement("input");
    inpUrl.type = "url";
    let inpPhotoJpegQuality = document.createElement("input");
    inpPhotoJpegQuality.type = "number";
    inpPhotoJpegQuality.min = "1";
    inpPhotoJpegQuality.max = "100";
    inpPhotoJpegQuality.placeholder = "Typically around 80";

    let [inpForceReloadFromNet, widgetForceReloadFromNet] = createSimpleSwitch();
    let [inpRemoteDebuggerEnabled, widgetRemoteDebuggerEnabled] = createSimpleSwitch();
    let [inpForwardConsoleLogToLogCat, widgetForwardConsoleLogToLogCat] = createSimpleSwitch();
    let [inpHapticFeedbackOnBarcodeRecognized, widgetHapticFeedbackOnBarcodeRecognized] = createSimpleSwitch();
    let [inpMayManageConnections, widgetMayManageConnections] = createSimpleSwitch();
    let [inpIsConnectionManager, widgetIsConnectionManager] = createSimpleSwitch();
    let [inpHapticFeedbackOnAutoFocused, widgetHapticFeedbackOnAutoFocused] = createSimpleSwitch();
    let [inpHasPermissionToTakePhoto, widgetHasPermissionToTakePhoto] = createSimpleSwitch();

    {
        let lbl = document.createElement("div");
        lbl.className = "label";
        lbl.textContent = "Connection name";
        result.appendChild(lbl);
        
        inpName.placeholder = "Some app name";
        if (ci.name != null) {
            inpName.value = ci.name;
        }    
        result.appendChild(inpName);
    }

    {
        let lbl = document.createElement("div");
        lbl.className = "label";
        lbl.textContent = "URL";  
        result.appendChild(lbl);
        
        inpUrl.placeholder = "https://example.com";
        if (ci.url != null) {
            inpUrl.value = ci.url;
        }
        result.appendChild(inpUrl);
    }

    {
        let lbl = document.createElement("div");
        lbl.className = "label";
        lbl.textContent = "[Developer] Force full web reloading on URL open?";  
        result.appendChild(lbl);
        
        if (ci.forceReloadFromNet != null) {
            inpForceReloadFromNet.checked = ci.forceReloadFromNet;
        }
        result.appendChild(widgetForceReloadFromNet);
    }

    {
        let lbl = document.createElement("div");
        lbl.className = "label";
        lbl.textContent = "[Developer] Enable Chrome remote debugging?";  
        result.appendChild(lbl);
            
        if (ci.remoteDebuggerEnabled != null) {
            inpRemoteDebuggerEnabled.checked = ci.remoteDebuggerEnabled;
        }
        result.appendChild(widgetRemoteDebuggerEnabled);
    }

    {
        let lbl = document.createElement("div");
        lbl.className = "label";
        lbl.textContent = "[Developer] Forward console.log to logcat?";  
        result.appendChild(lbl);
            
        if (ci.forwardConsoleLogToLogCat != null) {
            inpForwardConsoleLogToLogCat.checked = ci.forwardConsoleLogToLogCat;
        }
        result.appendChild(widgetForwardConsoleLogToLogCat);
    }
    
    {
        let lbl = document.createElement("div");
        lbl.className = "label";
        lbl.textContent = "[Developer] Haptic feedback on barcode recognized?";
        result.appendChild(lbl);
            
        if (ci.hapticFeedbackOnBarcodeRecognized != null) {
            inpHapticFeedbackOnBarcodeRecognized.checked = ci.hapticFeedbackOnBarcodeRecognized;
        }
        result.appendChild(widgetHapticFeedbackOnBarcodeRecognized);
    }
    
    {
        let lbl = document.createElement("div");
        lbl.className = "label";
        lbl.textContent = "[Sensitive] May edit connections?";
        result.appendChild(lbl);
            
        if (ci.mayManageConnections != null) {
            inpMayManageConnections.checked = ci.mayManageConnections;
        }
        result.appendChild(widgetMayManageConnections);
    }

    {
        let lbl = document.createElement("div");
        lbl.className = "label";
        lbl.textContent = "[Sensitive] Is the default connection manager?";
        result.appendChild(lbl);
            
        if (ci.isConnectionManager != null) {
            inpIsConnectionManager.checked = ci.isConnectionManager;
        }
        result.appendChild(widgetIsConnectionManager);
    }

    {
        let lbl = document.createElement("div");
        lbl.className = "label";
        lbl.textContent = "Haptic feedback on auto focused?";
        result.appendChild(lbl);
            
        if (ci.hapticFeedbackOnAutoFocused != null) {
            inpHapticFeedbackOnAutoFocused.checked = ci.hapticFeedbackOnAutoFocused;
        }
        result.appendChild(widgetHapticFeedbackOnAutoFocused);
    }

    
    {
        let lbl = document.createElement("div");
        lbl.className = "label";
        lbl.textContent = "[Sensitive] Has permission to take photo?";
        result.appendChild(lbl);
            
        if (ci.hasPermissionToTakePhoto != null) {
            inpHasPermissionToTakePhoto.checked = ci.hasPermissionToTakePhoto;
        }
        result.appendChild(widgetHasPermissionToTakePhoto);
    }

    {
        let lbl = document.createElement("div");
        lbl.className = "label";
        lbl.textContent = "JPEG compression (the higher the better)";
        result.appendChild(lbl);
            
        if (ci.photoJpegQuality != null) {
            inpPhotoJpegQuality.value = ci.photoJpegQuality.toString();
        }
        result.appendChild(inpPhotoJpegQuality);
    }
    
    let btnSave = document.createElement("input");
    btnSave.className = "save";
    btnSave.type = "button";
    btnSave.value = ci.persisted ? "Save change" : "Create";    
    result.appendChild(btnSave);
    
    btnSave.addEventListener("click", () => {
        let ci = new ConnectionInfo();
        ci.url = inpUrl.value;
        ci.name = inpName.value;
        ci.forceReloadFromNet = inpForceReloadFromNet.checked;
        ci.remoteDebuggerEnabled = inpRemoteDebuggerEnabled.checked;
        ci.forwardConsoleLogToLogCat = inpForwardConsoleLogToLogCat.checked;
        ci.hapticFeedbackOnBarcodeRecognized = inpHapticFeedbackOnBarcodeRecognized.checked;
        ci.isConnectionManager = inpIsConnectionManager.checked;
        ci.hapticFeedbackOnAutoFocused = inpHapticFeedbackOnAutoFocused.checked;
        ci.hasPermissionToTakePhoto = inpHasPermissionToTakePhoto.checked;
        ci.photoJpegQuality = parseInt(inpPhotoJpegQuality.value, 10);
        let succ : boolean = 
            JSON.parse(
                window.saveConnection(ciUrl, ci));
        if (ci.persisted) {
            console.log("altering succeeded?="+succ);
        } else {
            console.log("creating succeeded?="+succ);
        }
        onDone(ci.url);
    });
    
    return result;
}

function decodeQueryParams() : Map<string,string> {
    let q = document.location.href;
    let i = q.indexOf('#');
    if (i > 0) {
        q = q.substring(0, i);        
    }

    i = q.indexOf("?");
    if (i < 0) {
        return new Map();
    }
    
    let result = new Map();
    q
        .substring(i+1)
        .split('&')
        .forEach(x => {
            let keyAndValue = x.split('=');
            result.set(keyAndValue[0], keyAndValue[1]);
        })
    return result;
}

enum MenuMode {
    connect = "connect",
    edit = "edit",
    new = "new",
    remove = "remove"
}

function stringToMenuMode(inp : string|undefined) : MenuMode | null {
    switch (inp) {
        case "connect" : return MenuMode.connect;
        case "edit" : return MenuMode.edit;
        case "new" : return MenuMode.new;
        case "remove" : return MenuMode.remove;
        default: return null;
    }
}

window.addEventListener('load', (_) => {
    document.body.removeAllChildren();    
    let params = decodeQueryParams();
    
    let mode = stringToMenuMode(params.get("mode"));
    let url = params.get("url");

    switch(mode) {
        case MenuMode.connect: {
            let activateConnectionChooser = () => {};
            let activateConnectionCreator = () => {};
            let activateCreateShortcutCreator = () => {};
            let activateChooseItemToEditCreator = () => {};
            let activateChooseItemToRemoveCreator = () => {};

            let connectionCreator = () => createConnectionCreatorEditorForm(
                () => activateConnectionChooser(), 
                _ => activateConnectionChooser(), 
                new ConnectionInfo());
            let connectionChooser = () => createConnectionChooserForm(
                () => activateConnectionCreator(),
                () => activateCreateShortcutCreator(),
                () => activateChooseItemToEditCreator(),
                () => activateChooseItemToRemoveCreator() );
            let shortcutCreator = () => createShortcutForm(
                "Create shortcut",
                (x,onDone) => {
                    let res = window.createShortcut(x.url);
                    console.log("createShortcutForm success?="+res);
                    if (res === "true") {
                        window.showToast("Shortcut created", false);
                    }
                    onDone();
                },
                () => activateConnectionChooser());
            let connectionEditor = () => createShortcutForm(
                "Edit connection",
                (x,onDone) => {
                    console.log("editing connection="+x.url);
                    
                    var editor = createConnectionCreatorEditorForm(
                        () => onDone(),
                        _ => onDone(),
                        x
                    );

                    document.body.replaceChildrenWith(editor);
                },
                () => activateConnectionChooser());
            let connectionRemover = () => createShortcutForm(
                "Remove connection",
                (x,onDone) => {
                    let res = window.removeConnection(x);
                    console.log("removeConnection success?="+res);
                    if (res === "true") {
                        window.showToast("Connection removed", false);
                    }
                    onDone();
                },
                () => activateConnectionChooser());

            activateConnectionChooser = () => document.body.replaceChildrenWith(connectionChooser());
            activateConnectionCreator = () => document.body.replaceChildrenWith(connectionCreator());
            activateCreateShortcutCreator = () => document.body.replaceChildrenWith(shortcutCreator());
            activateChooseItemToEditCreator = () => document.body.replaceChildrenWith(connectionEditor());
            activateChooseItemToRemoveCreator = () => document.body.replaceChildrenWith(connectionRemover());

            activateConnectionChooser();
            break
        }
        
        case MenuMode.new: {
            if (url === undefined) {
                let err = document.createElement("div");
                err.textContent = "mode 'new' without url";
                document.body.appendChild(err);
                break;
            }
            url = window.decodeURIComponent(url);
            
            document.body.replaceChildrenWith(createConnectionCreatorEditorForm(
                () => window.finishConnectionManager(null),
                url => window.finishConnectionManager(url),
                ConnectionInfo.fromUrl(window.decodeURIComponent(url))
            ));
            break;
        }

        case MenuMode.edit: {
            if (url === undefined) {
                let err = document.createElement("div");
                err.textContent = "mode 'edit' without url";
                document.body.appendChild(err);
                break;
            }
        
            url = window.decodeURIComponent(url);

            let rawConnections = window.getKnownConnections();
            let conns = rawConnections.filter(x => x.url == url);

            if (url == null || conns.length != 1) {
                let err = document.createElement("div");
                err.textContent = "mode 'edit' for nonpersisted url: "+url;
                document.body.appendChild(err);
                break;
            }

            document.body.replaceChildrenWith(createConnectionCreatorEditorForm(
                () => window.finishConnectionManager(url as string), //above already verified that it's not null,
                url => window.finishConnectionManager(url),
                conns[0]
            ));
            break;
        }

        default: 
            let err = document.createElement("div");
            console?.log("url="+document.location.href);            
            err.textContent = "unknown mode";
            document.body.appendChild(err);
            break;
    }    
});        
