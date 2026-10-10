from PIL import Image, ImageDraw
S=1024
img=Image.new("RGB",(S,S),(28,37,48))
d=ImageDraw.Draw(img)
# subtle diagonal sheen
for i in range(S):
    c=int(28+10*i/S); d.line([(0,i),(S,i)],fill=(c,c+9,c+20))
o=(232,115,26)
def r(x0,y0,x1,y1): d.rectangle([x0*S,y0*S,x1*S,y1*S],fill=o)
r(0.22,0.20,0.78,0.31); r(0.22,0.69,0.78,0.80); r(0.445,0.31,0.555,0.69)
img.save("ios/WeightKit/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon.png")
img.resize((512,512)).save("shared/out/play_icon_512.png")
