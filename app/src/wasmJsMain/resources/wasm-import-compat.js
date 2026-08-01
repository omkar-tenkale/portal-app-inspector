(function () {
    function patchImports(importObject) {
        var jsCode = importObject && importObject.js_code;
        if (!jsCode) return importObject;

        if (typeof jsCode["kotlin.time.toFixed"] !== "function") {
            jsCode["kotlin.time.toFixed"] = function (value, decimals) {
                return Number(value).toFixed(decimals);
            };
        }

        if (typeof jsCode["kotlin.time.toPrecision"] !== "function") {
            jsCode["kotlin.time.toPrecision"] = function (value, decimals) {
                return Number(value).toPrecision(decimals);
            };
        }

        return importObject;
    }

    var instantiate = WebAssembly.instantiate;
    WebAssembly.instantiate = function (source, importObject) {
        return instantiate.apply(this, [source, patchImports(importObject)].concat(Array.prototype.slice.call(arguments, 2)));
    };

    if (WebAssembly.instantiateStreaming) {
        var instantiateStreaming = WebAssembly.instantiateStreaming;
        WebAssembly.instantiateStreaming = function (source, importObject) {
            return instantiateStreaming.apply(this, [source, patchImports(importObject)].concat(Array.prototype.slice.call(arguments, 2)));
        };
    }
})();
