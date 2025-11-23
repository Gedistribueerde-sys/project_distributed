
# Uitleg van `b`, `B(b)`, `v`, `add` en `get`

## Wat is `b`?

`b` is de geheime tag.
Het is een willekeurige waarde die alleen de twee clients kennen.
Elke richting van de chat heeft een eigen tag (`outTag` en `inTag`).

## Wat is `B(b)`?

`B(b)` is de hash van de tag `b`.
De server krijgt nooit `b` zelf, maar alleen `B(b)`.

---

# Wat verstuurt de client?

## `v` (ciphertext)

`v` is de versleutelde payload:

```
v = Encrypt_K( m || nextIdx || nextb )
```

Deze payload bevat:

* `m` : het bericht
* `nextIdx` : de volgende index
* `nextb` : de nieuwe geheime tag

---

# Hoe werkt `add(i, v, t)`?

Bij het versturen doet de client:

```
t = B(b)
add(i, v, t)
```

* `i` = huidige index
* `v` = encrypted payload
* `t` = hash van de huidige tag

De server slaat `v` en `t` op in cel `i`.

---

# Hoe werkt `get(i, b)`?

Bij het ophalen doet de client:

```
get(i, b)
```

De server berekent zelf:

```
t = B(b)
```

en zoekt in cel `i` naar een paar `<v, t>`.
Als het bestaat, wordt `v` teruggegeven en uit de cel verwijderd.

De client decrypt daarna `v` om `m`, `nextIdx` en `nextb` te krijgen en werkt de eigen staat bij.



# TODO 

## 1 redundancy in de server

## 2 : bij uitloggen -> de data opslaan in de keystore van de client ( +- redundancy in de client )
(ook evt de chats zelf)

## 3 : netwerk beveileging via tor
