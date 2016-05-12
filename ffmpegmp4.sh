#ffmpeg -i testdata/zoomoo/76fab9ea8d4dc3941bd0872b7bef2c9c_31321.ts -vcodec copy -an -y tmp/hlsjs/chunk.mp4
#ffmpeg -v trace -i testdata/zoomoo/4760a09c958138d875af68fd53f4a9a8_80917.ts -vcodec copy -an -y tmp/hlsjs/80917.mp4
ffmpeg -v trace -i testdata/zoomoo/all.ts -vcodec copy -an -y tmp/hlsjs/all.mp4

