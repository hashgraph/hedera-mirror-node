const statusField = "status";

function isSuccess(response){
  if(!response.hasOwnProperty(statusField)){
    return false
  }

  return response.status >= 200 && response.status < 300;
}

function isValidListResponse(response, listName) {
  if(!isSuccess(response)){
    return false;
  }

  const body = JSON.parse(response.body);
  const list = body[listName];

  if (!Array.isArray(list)) {
    return false;
  }

  return list.length > 0;
}

export { isValidListResponse, isSuccess };
