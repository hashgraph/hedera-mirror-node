const statusField = "status";

function isListValid(list){
  return isListNonEmpty(list) && list.length <= __ENV.DEFAULT_LIMIT;
}

function isListNonEmpty(list){
  if(!Array.isArray(list)){
    return false;
  }

  return list.length > 0;
}

function extractListFromResponse(response, listName){
  if(!isSuccess(response)){
    return null;
  }
  const body = JSON.parse(response.body);

  return body[listName];
}

function responseHasListWithValidSize(response, listName){
  const list = extractListFromResponse(response, listName);
  return isListValid(list);
}

function responseHasNonEmptyList(response, listName){
  const list = extractListFromResponse(response, listName);
  return isListNonEmpty(list);
}

function responseHasListWithSize(response, listName, size){
  const list = extractListFromResponse(response, listName);
  if(!Array.isArray(list)){
    return false;
  }

  return list.length === size;
}

function isSuccess(response){
  if(!response.hasOwnProperty(statusField)){
    return false
  }

  return response.status >= 200 && response.status < 300;
}

export { responseHasListWithValidSize, responseHasNonEmptyList, responseHasListWithSize, isSuccess };
