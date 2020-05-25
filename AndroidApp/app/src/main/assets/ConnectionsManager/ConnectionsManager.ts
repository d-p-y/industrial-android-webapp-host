///<reference path='../contracts.ts'/>
///<reference path='../richer_implementations.ts'/>

class ConnectionInfo {
    public persisted : boolean = false;

    public url : string = "";
    public name : string = "";

    public static fromUrl(url : string) {
        let res = new ConnectionInfo();
        res.url = url;
        return res;
    }
}

function createShortcutForm(onBack : (() => void)) : Node {
    document.title = "Create shortcut";
    window.setToolbarBackButtonState(true);
    window.androidBackConsumed = () => {
        onBack();
        return true
    };
    
    let rawConnections : ConnectionInfo[] = JSON.parse( window.getKnownConnections());

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
                    let res = window.createShortcut(x.url);
                    console.log("createShortcutForm success?="+res);
                    window.showToast("Shortcut created!", false);
                    onBack();
                });
                btn.value = x.name;
                return btn;
            });
                       
    result.appendChildren(btns);

    return result;
}

function createConnectionChooserForm(activateNewConnectionScreen : (() => void), activateCreateShortcutScreen : (() => void)) : Node {
    document.title = "Connect to";
    window.setToolbarBackButtonState(false);
    window.androidBackConsumed = () => false;

    let rawConnections : ConnectionInfo[] = JSON.parse(window.getKnownConnections());

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


    let addAnother = document.createElement("a");
    addAnother.href = "#";
    addAnother.textContent = "Add connection...";
    addAnother.addEventListener("click", ev => {
        ev.preventDefault(); 
        console.log("switching to createConnection");
        activateNewConnectionScreen();
    });
    result.appendChild(addAnother);

    
    let createShortcut = document.createElement("a");
    createShortcut.href = "#";
    createShortcut.textContent = "Create shortcut...";
    createShortcut.addEventListener("click", ev => {
        ev.preventDefault(); 
        console.log("switching to createShortcut");
        activateCreateShortcutScreen();
    });
    result.appendChild(createShortcut);


    return result;
}

function createConnectionCreatorEditorForm(onBack : (() => void) | null, onDone : ((url:string) => void), ci : ConnectionInfo) : Node {
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
    inpName.id = "inpName";
    if (ci.name != null) {
        inpName.value = ci.name;
    }
    

    let lblName = document.createElement("label");
    lblName.textContent = "Connection name";
    lblName.htmlFor = inpName.id;

    result.appendChild(lblName);
    result.appendChild(inpName);



    let inpUrl = document.createElement("input");
    inpUrl.id = "inpUrl";
    inpUrl.placeholder = "https://example.com";
    if (ci.url != null) {
        inpUrl.value = ci.url;
    }
    

    let lblUrl = document.createElement("label");
    lblUrl.textContent = "URL";
    lblUrl.htmlFor = inpUrl.id;

    result.appendChild(lblUrl);
    result.appendChild(inpUrl);


    let btnSave = document.createElement("input");
    btnSave.type = "button";
    btnSave.value = ci.persisted ? "Save change" : "Create";
    btnSave.style.gridColumn = "1/3";
    result.appendChild(btnSave);
    
    btnSave.addEventListener("click", () => {
        let ci = new ConnectionInfo();
        ci.url = inpUrl.value;
        ci.name = inpName.value;
        let succ : boolean = 
            JSON.parse(
                window.saveConnection(
                    JSON.stringify(ci)));
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
    choice = "choice",
    edit = "edit",
    new = "new"
}


function stringToMenuMode(inp : string|undefined) : MenuMode | null {
    switch (inp) {
        case "choice" : return MenuMode.choice;
        case "edit" : return MenuMode.edit;
        case "new" : return MenuMode.new;
        default: return null;
    }
}

window.addEventListener('load', (_) => {
    document.body.removeAllChildren();
    document.body.style.backgroundColor = "#e6ffff";
    document.body.style.display = "flex";
    document.body.style.flexDirection = "column";
    document.body.style.height = "100vh";
    
    let params = decodeQueryParams();
    
    let mode = stringToMenuMode(params.get("mode"));
    let url = params.get("url");

    switch(mode) {
        case MenuMode.choice: {
            let activateConnectionChooser = () => {};
            let activateConnectionCreator = () => {};
            let activateCreateShortcutCreator = () => {};

            let connectionCreator = () => createConnectionCreatorEditorForm(
                () => activateConnectionChooser(), 
                _ => activateConnectionChooser(), 
                new ConnectionInfo());
            let connectionChooser = () => createConnectionChooserForm(
                () => activateConnectionCreator(),
                () => activateCreateShortcutCreator());
            let shortcutCreator = () => createShortcutForm(() => activateConnectionChooser());

            activateConnectionChooser = () => document.body.replaceChildrenWith(connectionChooser());
            activateConnectionCreator = () => document.body.replaceChildrenWith(connectionCreator());
            activateCreateShortcutCreator = () => document.body.replaceChildrenWith(shortcutCreator());
                
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

            let rawConnections : ConnectionInfo[] = JSON.parse( window.getKnownConnections());
            let conns = rawConnections.filter(x => x.url == url);

            if (conns.length != 1) {
                let err = document.createElement("div");
                err.textContent = "mode 'edit' for nonpersisted url: "+url;
                document.body.appendChild(err);
                break;
            }

            document.body.replaceChildrenWith(createConnectionCreatorEditorForm(
                null,
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
