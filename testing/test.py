import requests
from PIL import Image
from io import BytesIO

# API endpoint
url = "http://IOTCamerasLB-1851832363.us-east-1.elb.amazonaws.com:80/api/camera/2/frame"

# Fetch the image as bytes
response = requests.get(url)
print(response)
if response.status_code == 200:
    # Convert bytes to an image
    img = Image.open(BytesIO(response.content))
    
    # Display the image (optional)
    img.show()

    # Save the image to a file
    img.save("frame.png")
else:
    print("Failed to fetch image:", response.status_code)
