# Reguły R8 dla wariantu release.
#
# Większość bibliotek (Compose, Health Connect, AndroidX) dostarcza własne
# reguły razem z zależnością, więc nie trzeba ich tu powtarzać. Zostaje to,
# czego R8 nie ma jak wywnioskować.

# Nazwy klas w śladach wyjątków. Bez tego każdy błąd z telefonu byłby ciągiem
# jednoliterowych nazw, a nie mamy tu procesu odwzorowywania śladów.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Parsery BLE i modele zapisywane do SQLite są używane wyłącznie z kodu
# aplikacji, więc R8 poradziłby sobie sam. Zostawiamy jednak nazwy pól modeli:
# kolumny w bazie zakładamy ręcznie i porównywanie jednego z drugim przy
# diagnozowaniu migracji jest wtedy możliwe.
-keepclassmembernames class pl.gymbro.data.** { <fields>; }

# MapLibre dostarcza własne reguły R8 razem z biblioteką.
