# TODO

### SIGIL encrypted chat (queued behind the testrun2 speedrun work)
The spec and reference implementation are in `docs/sigil/`. Treat `sigil.py` as the source of truth for interop test vectors.
- Port the SIGIL core to pure Java with the JCA built-ins: AES-256-GCM, PBKDF2-HMAC-SHA256 at 210k iterations, ECDH P-256 and HKDF-SHA256. No new dependencies. Add unit tests that round-trip with tokens from `sigil.py`.
- Butler: in `WhisperChecker`/`Butler`, decrypt inbound `S1C.`/`S1K.`/`S1E.` whispers (including `i/n` fragments) before auth and command parsing. Reply sealed when the request arrived sealed.
    - Config: circle name and passphrase, plus a signet keypair, in `ButlerConfig`.
    - Optional setting: require sealed commands.
- Player option (off by default):
    - A toggle that auto-decrypts SIGIL tokens seen in chat for known circles and contacts, then shows the plaintext locally, marked as decrypted.
    - A `@seal <circle|player> <msg>` command that sends the sealed token.
- Keys are stored like a password file under the config dir. Never log a passphrase or plaintext at info level.
- Run PBKDF2 (about 0.3s) once per circle and cache the key. Do not run it on the render or tick thread.


### Misc
- Implement configs.
- Allow any bed color when crafting beds from wool.
- Add cherry blossom wood as a valid wood type or something? It doesn't seem to work properly atm.
- Maybe add some sort of system that finds and uses seed of the current world?


### Common death causes
- Improve escaping from lava.
- Prevent from looking endermen in the eyes.
- Do not hit pigmen in the nether (or implement a special behaviour when do).
- Maybe avoid bastions somehow?
