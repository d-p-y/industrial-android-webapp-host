///<reference path='../contracts.ts'/>
///<reference path='../richer_implementations.ts'/>

class ConnectionInfo {
    public url : string = "";
    public name : string = "";
}

window.addEventListener('load', (_) => {
    document.body.removeAllChildren();
    document.body.style.backgroundColor = "#e6ffff";
    document.body.style.display = "flex";
    document.body.style.flexDirection = "column";
    document.body.style.height = "100vh";
    
    let rawConnections : ConnectionInfo[] = JSON.parse( window.getKnownConnections());

    let connectionsContainer = document.createElement("div");
    connectionsContainer.style.display = "flex";
    connectionsContainer.style.flexDirection = "column";

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

            
    connectionsContainer.appendChildren(btns);

    document.body.appendChild(connectionsContainer);

    window.setTitle("Connect to");
});        
