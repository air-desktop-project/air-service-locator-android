# Règles de rétrécissement pour la variante `release`.
#
# VIDE, ET CE N'EST PAS UN OUBLI. R8 déduit seul ce qu'il peut retirer tant que
# rien n'atteint une classe par réflexion. Une règle `-keep` posée d'avance
# « au cas où » désactive silencieusement l'optimisation qu'elle nomme, et
# personne ne se souvient jamais de la retirer.
#
# La première règle qui entrera ici viendra d'un plantage observé en `release`,
# et son commentaire dira lequel.
