import requests
from PIL import Image
from io import BytesIO

# API endpoint
url = "http://localhost:8080/api/camera/5/frame"

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
