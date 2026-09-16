(function () {
    'use strict';
    if (window.location.origin !== 'https://srtools.neonteam.dev' ||
            window.__pearlSyncResponseGuard || typeof window.fetch !== 'function') {
        return;
    }
    window.__pearlSyncResponseGuard = true;
    var originalFetch = window.fetch;
    window.fetch = function (input, options) {
        var localSync = false;
        try {
            var url = new URL(typeof input === 'string' ? input : input.url || input,
                window.location.href);
            localSync = url.protocol === 'http:' &&
                (url.hostname === 'localhost' || url.hostname === '127.0.0.1') &&
                url.port === '21000' && url.pathname === '/srtools';
        } catch (ignored) { }
        return originalFetch.apply(this, arguments).then(function (response) {
            if (!localSync || !response.ok) return response;
            // Dispatch reports write errors in JSON while returning HTTP 200.
            // Reject these so SRTools cannot display a false Sync successful toast.
            return response.clone().json().then(function (body) {
                if (body && Number(body.status) >= 400) {
                    throw new Error(body.message || 'Sync failed');
                }
                return response;
            }, function () {
                return response;
            });
        });
    };
})();
