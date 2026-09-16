const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const test = require('node:test');
const script = fs.readFileSync('app/src/main/assets/srtools-sync.js', 'utf8');

function install(reply, origin = 'https://srtools.neonteam.dev') {
    const original = (...args) => Promise.resolve(reply(...args));
    const window = { location: { origin, href: origin + '/1001/detail' }, fetch: original };
    const context = vm.createContext({ window, URL });
    vm.runInContext(script, context);
    return { window, context, original };
}

for (const host of ['localhost', '127.0.0.1']) {
    test('rejects a real Dispatch write-failure envelope on ' + host, async () => {
        const { window } = install(() => new Response(JSON.stringify({
            message: 'failed to write freesr-data.json', status: 500
        })));
        await assert.rejects(window.fetch('http://' + host + ':21000/srtools', {
            method: 'POST', body: '{"data":{}}'
        }), /failed to write freesr-data.json/);
    });
}

test('successful probes and Sync keep the response body readable', async () => {
    const response = new Response('{"message":"OK","status":200}');
    const { window } = install(() => response);
    const result = await window.fetch(new Request('http://localhost:21000/srtools'));
    assert.equal(result, response);
    assert.deepEqual(await result.json(), { message: 'OK', status: 200 });
});

test('does not reinterpret unrelated requests or remote servers', async () => {
    const { window } = install(() => new Response('{"status":500}'));
    for (const url of ['https://srtools.neonteam.dev/api/data',
        'http://example.com:21000/srtools', 'http://localhost:21001/srtools',
        'http://localhost:21000/other']) {
        assert.equal((await window.fetch(url)).status, 200);
    }
});

test('passes through HTTP errors, non-JSON responses and network errors', async () => {
    const url = new URL('http://localhost:21000/srtools');
    const { window: http } = install(() => new Response('failed', { status: 500 }));
    assert.equal((await http.fetch(url)).status, 500);
    const { window: text } = install(() => new Response('plain text'));
    assert.equal(await (await text.fetch(url)).text(), 'plain text');
    const { window: network } = install(() => Promise.reject(new Error('network failed')));
    await assert.rejects(network.fetch(url), /network failed/);
});

test('installs only once, and only for the trusted website', () => {
    const { window, context } = install(() => new Response(''));
    const guarded = window.fetch;
    vm.runInContext(script, context);
    assert.equal(window.fetch, guarded);
    const other = install(() => new Response(''), 'https://example.com');
    assert.equal(other.window.fetch, other.original);
});
