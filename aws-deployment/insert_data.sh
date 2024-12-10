#!/bin/bash

export AWS_REGION="us-east-1" 

aws dynamodb put-item --region $AWS_REGION --table-name CameraAccesses --item '{"cameraID": {"N": "1"}, "frameData": {"S": "{\"nextVideoPart\":1,\"nextFrameToUse\":0,\"accessedAt\":null}"}}'
aws dynamodb put-item --region $AWS_REGION --table-name CameraAccesses --item '{"cameraID": {"N": "2"}, "frameData": {"S": "{\"nextVideoPart\":1,\"nextFrameToUse\":0,\"accessedAt\":null}"}}'
aws dynamodb put-item --region $AWS_REGION --table-name CameraAccesses --item '{"cameraID": {"N": "3"}, "frameData": {"S": "{\"nextVideoPart\":1,\"nextFrameToUse\":0,\"accessedAt\":null}"}}'
aws dynamodb put-item --region $AWS_REGION --table-name CameraAccesses --item '{"cameraID": {"N": "4"}, "frameData": {"S": "{\"nextVideoPart\":1,\"nextFrameToUse\":0,\"accessedAt\":null}"}}'
aws dynamodb put-item --region $AWS_REGION --table-name CameraAccesses --item '{"cameraID": {"N": "5"}, "frameData": {"S": "{\"nextVideoPart\":1,\"nextFrameToUse\":0,\"accessedAt\":null}"}}'
