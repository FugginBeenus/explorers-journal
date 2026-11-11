from PIL import Image
def fit(im, W, H):
    w, h = im.size
    s = max(W / w, H / h)
    im = im.resize((round(w * s), round(h * s)), Image.BOX)
    x = (im.size[0] - W) // 2; y = (im.size[1] - H) // 2
    return im.crop((x, y, x + W, y + H))
