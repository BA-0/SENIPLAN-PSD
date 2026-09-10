# -*- coding: utf-8 -*-
"""
Repose le mot-symbole SENICO dans le ciel de la photo de connexion.

La photo livrée par le client porte déjà un lettrage, mais c'est une imitation
délavée : le soleil qu'elle a derrière elle le vide de sa couleur, le bas du
« c » et du « o » s'y dissout complètement — l'écart de luminance avec le ciel
y tombe à 1 sur 255 —, et le point du « i » y est rose au lieu de vert. Aucun
masque ne peut récupérer une forme qui n'est plus dans l'image : on efface donc
ce lettrage et on repose `logo-senico-mark.png`, le logo officiel, celui-là
même que `BrandLockup` affiche en net sous `lg`.

Deux étapes.

1. Effacer. Le masque du lettrage vient d'un fond reconstruit par ouverture et
   fermeture *horizontales* de 121 px : elles effacent tout ce qui est plus
   étroit que ça sur une ligne — les traits du lettrage, larges de 90 px au
   plus — mais gardent l'horizon et la côte, qui courent sur toute l'image. Le
   masque est ensuite dilaté, puis les pixels qu'il couvre sont réinterpolés
   ligne à ligne entre leurs voisins restés visibles. Le ciel étant lisse sur
   les 100 px que fait au plus un trait, la reprise ne se voit pas.

2. Reposer. Le mark est mis à l'échelle de la largeur du lettrage effacé
   (584 px), sans déformation, et sa ligne de base est calée sur l'ancienne :
   la mise en page de `login/page.tsx`, qui compte sur un lettrage allant de
   40 % à 71 % de la largeur et s'arrêtant vers 29,6 vh, ne bouge pas. Une
   ombre portée et un voile de soleil le rattachent à la photo, sinon il y est
   posé comme un autocollant.

Usage : python frontend/scripts/recolor-logo.py   (depuis la racine du dépôt)
"""

import os
import numpy as np
from PIL import Image, ImageFilter
from scipy import ndimage

RACINE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(RACINE, "public", "logo senico new.jpg")
MARK = os.path.join(RACINE, "public", "logo-senico-mark.png")
SORTIE = os.path.join(RACINE, "public", "login-bg-senico.jpg")

# Boîte de l'ancien lettrage, avec une marge : au-delà on ne touche à rien.
X0, X1, Y0, Y1 = 392, 1002, 103, 232
# Empreinte du lettrage de la photo, relevée sur le masque : c'est elle que le
# mark officiel vient remplacer, largeur et ligne de base comprises.
LETTRES_X0, LETTRES_X1, LETTRES_BAS = 405, 988, 226

LARGEUR_FOND = 121      # ouverture/fermeture horizontale, en pixels
DILATATION = 5          # marge d'effacement autour du masque, en pixels


def fond_horizontal(canal):
    """Le ciel et le paysage sans le lettrage."""
    x = ndimage.maximum_filter1d(canal, LARGEUR_FOND, axis=1, mode="nearest")
    x = ndimage.minimum_filter1d(x, LARGEUR_FOND, axis=1, mode="nearest")
    x = ndimage.minimum_filter1d(x, LARGEUR_FOND, axis=1, mode="nearest")
    return ndimage.maximum_filter1d(x, LARGEUR_FOND, axis=1, mode="nearest")


def luminance(img):
    return 0.299 * img[..., 0] + 0.587 * img[..., 1] + 0.114 * img[..., 2]


def verdeur(img):
    return img[..., 1] - 0.5 * (img[..., 0] + img[..., 2])


def masque_ancien_lettrage(photo):
    """Tout ce qui, dans la boîte, n'appartient pas au ciel.

    Large exprès : ce masque ne sert qu'à effacer, et effacer un peu de ciel
    autour des traits ne coûte rien puisqu'on le remplace par du ciel. On
    cumule l'écart de luminance et l'écart de verdeur — le second attrape les
    traits que le soleil a rendus aussi clairs que le ciel mais qui restent
    verts sur son orange.
    """
    fond = np.dstack([fond_horizontal(photo[..., c]) for c in range(3)])
    ecart = np.abs(luminance(photo) - luminance(fond))
    vert = verdeur(photo) - verdeur(fond)

    boite = np.zeros(ecart.shape, bool)
    boite[Y0:Y1, X0:X1] = True
    m = ((ecart > 7) | (vert > 5)) & boite
    m = ndimage.binary_closing(m, structure=np.ones((5, 5), bool))
    m = ndimage.binary_dilation(m, structure=np.ones((3, 3), bool), iterations=DILATATION)
    return m & boite


def effacer(photo, m):
    """Réinterpole horizontalement les pixels masqués."""
    sortie = photo.copy()
    largeur = photo.shape[1]
    colonnes = np.arange(largeur)
    for y in range(Y0, Y1):
        ligne = m[y]
        if not ligne.any():
            continue
        visible = ~ligne
        for c in range(3):
            sortie[y, :, c] = np.interp(colonnes, colonnes[visible], photo[y, visible, c])
    # L'interpolation ligne à ligne laisse de fines cassures d'une ligne à
    # l'autre ; un flou léger, appliqué seulement sous le masque, les efface.
    flou = np.dstack([ndimage.gaussian_filter(sortie[..., c], 2.0) for c in range(3)])
    poids = ndimage.gaussian_filter(m.astype(np.float32), 2.0)[..., None]
    return sortie * (1 - poids) + flou * poids


def poser_mark(fond_efface, photo):
    mark = Image.open(MARK).convert("RGBA")
    p = np.asarray(mark).astype(np.float32)
    lettres = (p[..., 3] > 128) & (p[..., 1] > p[..., 0] + 20)
    ys, xs = np.nonzero(lettres)
    # Le point du « i » est vert lui aussi : on cale sur le corps des lettres,
    # pas sur lui, d'où le filtrage des lignes du haut.
    corps = ys > ys.min() + 20
    gx0, gx1 = xs[corps].min(), xs[corps].max()
    gy1 = ys[corps].max()

    echelle = (LETTRES_X1 - LETTRES_X0) / float(gx1 - gx0)
    largeur = int(round(mark.width * echelle))
    hauteur = int(round(mark.height * echelle))
    mark = mark.resize((largeur, hauteur), Image.LANCZOS)
    ox = int(round(LETTRES_X0 - gx0 * echelle))
    oy = int(round(LETTRES_BAS - gy1 * echelle))

    couche = np.zeros(fond_efface.shape[:2] + (4,), np.float32)
    couche[oy:oy + hauteur, ox:ox + largeur] = np.asarray(mark).astype(np.float32)
    couleur, alpha = couche[..., :3], couche[..., 3:] / 255.0

    # Ombre portée : sans elle le mark flotte au-dessus de la photo.
    ombre = ndimage.gaussian_filter(np.roll(np.roll(alpha[..., 0], 3, 0), 2, 1), 3.0) * 0.30
    sortie = fond_efface * (1 - ombre[..., None])

    # Et le soleil continue de passer légèrement devant, là où le ciel brûle.
    halo = np.clip((luminance(photo) - 205) / 50, 0, 1)[..., None] * 0.13
    couleur = couleur * (1 - halo) + np.array([255, 246, 232], np.float32) * halo
    return sortie * (1 - alpha) + couleur * alpha


def main():
    photo = np.asarray(Image.open(SOURCE).convert("RGB")).astype(np.float32)
    m = masque_ancien_lettrage(photo)
    sortie = poser_mark(effacer(photo, m), photo)
    Image.fromarray(np.clip(sortie, 0, 255).astype(np.uint8)).save(
        SORTIE, quality=90, subsampling=0, optimize=True
    )
    print("ecrit :", SORTIE, "-", os.path.getsize(SORTIE) // 1024, "Ko")


if __name__ == "__main__":
    main()
